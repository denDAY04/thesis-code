package edu.dk.asj.dpm.network;


import edu.dk.asj.dpm.network.packets.IdentityPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.network.packets.SAEParameterPacket;
import edu.dk.asj.dpm.network.packets.SAETokenPacket;
import edu.dk.asj.dpm.security.SAEParameterSpec;
import edu.dk.asj.dpm.security.SAESession;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.util.BufferHelper;
import edu.dk.asj.dpm.util.NetworkInterfaceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerConnection extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnection.class);

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 10L;
    private static final int SAE_BUFFER_CAPACITY = 1000;
    private static final long SAE_HANDSHAKE_TIMEOUT_MS = 100;

    private final AsynchronousServerSocketChannel connectionListener;
    private final PacketHandler packetHandler;
    private final int port;
    private final UUID nodeId;

    private AsynchronousSocketChannel connection;

    private ServerConnection(PacketHandler packetHandler, UUID nodeId) {
        this.packetHandler = packetHandler;
        this.nodeId = nodeId;
        try {
            SocketAddress nicWithRandPort = new InetSocketAddress(NetworkInterfaceHelper.getNetworkInterfaceAddress(), 0);
            connectionListener = AsynchronousServerSocketChannel.open().bind(nicWithRandPort);
            port = ((InetSocketAddress) connectionListener.getLocalAddress()).getPort();
            setName("server:" + port);
            LOGGER.debug("Opened server connection listener on {}", connectionListener.getLocalAddress());

        } catch (IOException e) {
            LOGGER.error("Could not bind server socket", e);
            cleanUp();
            throw new IllegalStateException("Failed to open server connection");
        }
    }

    public static ServerConnection open(PacketHandler packetHandler, UUID nodeId) {
        Objects.requireNonNull(packetHandler, "Request processor must not be null");
        Objects.requireNonNull(nodeId, "Node identity must not be null");

        ServerConnection connection = new ServerConnection(packetHandler, nodeId);
        connection.start();

        LOGGER.info("Started server connection");
        return connection;
    }

    @Override
    public void run() {
        if (!acceptConnection()) {
            cleanUp();;
            return;
        }

        byte[] encryptionKey = saeHandshake();
        if (encryptionKey == null) {
            LOGGER.warn("SAE handshake failed");
            packetHandler.error("Could not authenticate connection");
            cleanUp();
            return;
        }
        LOGGER.debug("Established secure connection");

        // todo ensure encryption on subsequent data

        Packet request = receiveRequest();
        if (request == null) {
            cleanUp();;
            return;
        }

        Packet response = packetHandler.process(request);
        if (response != null) {
            sendResponse(response);
        }

        cleanUp();
    }

    /**
     * Get the server's port number after it has been opened.
     * @return the port number.
     */
    public int getPort() {
        return port;
    }

    @Override
    public synchronized void close() {
        cleanUp();
    }

    private boolean acceptConnection() {
        LOGGER.debug("Waiting for connections");
        Future<AsynchronousSocketChannel> acceptPromise = connectionListener.accept();

        try {
            connection = acceptPromise.get(TIMEOUT, TimeUnit.SECONDS);
            LOGGER.debug("Accepted connection");
            return true;

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for request");
            acceptPromise.cancel(true);
            packetHandler.error("An error occurred while waiting for a server request");

        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while waiting for request", e);
            acceptPromise.cancel(true);
            packetHandler.error("An error occurred while waiting for a server request");

        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while waiting for request");
            acceptPromise.cancel(true);
        }

        return false;
    }

    private Packet receiveRequest() {
        LOGGER.debug("Receiving packet");
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> promise = connection.read(buffer);

        try {
            promise.get(TIMEOUT, TimeUnit.SECONDS);
            LOGGER.debug("Received packet");
            return Packet.deserialize(BufferHelper.readAndClear(buffer));

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving request");
            promise.cancel(true);
            packetHandler.error("An error occurred while receiving a node response");

        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception during receive", e);
            promise.cancel(true);
            packetHandler.error("An error occurred while receiving a node response");

        } catch (TimeoutException e) {
            LOGGER.warn("Receive timed out");
            promise.cancel(true);
            packetHandler.error("No response to server");
        }

        return null;
    }

    private void sendResponse(Packet response) {
        LOGGER.debug("Sending response");
        ByteBuffer buffer = ByteBuffer.wrap(response.serialize());
        Future<Integer> promise = connection.write(buffer);

        try {
            promise.get(TIMEOUT, TimeUnit.SECONDS);
            LOGGER.debug("Response sent");

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending response");
            promise.cancel(true);
            packetHandler.error("An error occurred while sending server response");

        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while sending response", e);
            promise.cancel(true);
            packetHandler.error("An error occurred while sending server response");

        } catch (TimeoutException e) {
            LOGGER.warn("Send timed out");
            promise.cancel(true);
            packetHandler.error("Could not send server response");
        }
    }

    private void cleanUp() {
        LOGGER.info("Cleaning up");
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up connection", e);
            }
        }

        if (connectionListener != null && connectionListener.isOpen()) {
            try {
                connectionListener.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up server", e);
            }
        }
    }

    private byte[] saeHandshake() {
        LOGGER.debug("Initiating SAE handshake");

        UUID remoteId = exchangeSAEIdentities();
        if (remoteId != null) {
            SAESession session = SecurityController.getInstance().initiateSaeSession(nodeId, remoteId);
            SAEParameterSpec remoteParameters = exchangeSAEParameters(session.getParameters());
            if (remoteParameters != null) {
                byte[] token = SecurityController.getInstance().generateSAEToken(session, remoteParameters);
                byte[] remoteToken = exchangeSAETokens(token);
                if (remoteToken != null) {
                    return SecurityController.getInstance().validateSAEToken(session, remoteToken, remoteParameters);
                }
            }
        }
        return  null;
    }

    private UUID exchangeSAEIdentities() {
        ByteBuffer idBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveIdPromise = connection.read(idBuffer);
        int receivedBytes = -1;
        try {
            receivedBytes =  receiveIdPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE identity");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE identity", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE identity");
        }

        if (receivedBytes < 1) {
            return null;
        } else {
            LOGGER.debug("Received SAE identity");
        }

        Packet request = Packet.deserialize(BufferHelper.readAndClear(idBuffer));
        if (!(request instanceof IdentityPacket)) {
            LOGGER.warn("Received invalid SAE identity response");
            return null;
        }

        Packet identityResponse = new IdentityPacket(nodeId);
        Future<Integer> sendIdPromise = connection.write(ByteBuffer.wrap(identityResponse.serialize()));
        int sentBytes = -1;
        try {
            sentBytes = sendIdPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE identity");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE identity", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE identity");
        }

        if (sentBytes < 1){
            return null;
        } else {
            LOGGER.debug("Sent SAE identity");
        }

        return ((IdentityPacket) request).getNodeId();
    }

    private SAEParameterSpec exchangeSAEParameters(SAEParameterSpec parameters) {
        ByteBuffer parameterBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveParamPromise = connection.read(parameterBuffer);
        int receivedBytes = -1;
        try {
            receivedBytes = receiveParamPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE parameters");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE parameters", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE parameters");
        }

        if (receivedBytes < 1) {
            return null;
        } else {
            LOGGER.debug("Received SAE parameters");
        }

        Packet parameterRequest = Packet.deserialize(BufferHelper.readAndClear(parameterBuffer));
        if (!(parameterRequest instanceof SAEParameterPacket)) {
            LOGGER.warn("Received invalid SAE parameter request");
            return null;
        }

        Packet parameterResponse = new SAEParameterPacket(parameters);
        Future<Integer> sendParamPromise = connection.write(ByteBuffer.wrap(parameterResponse.serialize()));
        int sentBytes = -1;
        try {
            sentBytes = sendParamPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE parameters");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE parameters", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE parameters");
        }

        if (sentBytes < 1) {
            return null;
        } else {
            LOGGER.debug("Sent SAE parameters");
        }

        return ((SAEParameterPacket) parameterRequest).getParameters();
    }

    private byte[] exchangeSAETokens(byte[] token) {
        ByteBuffer tokenBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveTokenPromise = connection.read(tokenBuffer);
        int receivedBytes = -1;
        try {
            receivedBytes = receiveTokenPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving SAE token");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while receiving SAE token", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while receiving SAE token");
        }

        if (receivedBytes < 1) {
            return null;
        } else {
            LOGGER.debug("Received SAE token");
        }

        Packet tokenRequest = Packet.deserialize(BufferHelper.readAndClear(tokenBuffer));
        if (!(tokenRequest instanceof SAETokenPacket)) {
            LOGGER.warn("Received invalid SAE token response");
            return null;
        }

        Packet tokenResponse = new SAETokenPacket(token);
        Future<Integer> sendTokenPromise = connection.write(ByteBuffer.wrap(tokenResponse.serialize()));
        int sentBytes = -1;
        try {
            sentBytes = sendTokenPromise.get(SAE_HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending SAE token");
        } catch (ExecutionException e) {
            LOGGER.warn("Unexpected exception while sending SAE token", e);
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while sending SAE token");
        }

        if (sentBytes < 1) {
            return null;
        } else {
            LOGGER.debug("Sent SAE token");
        }

        return ((SAETokenPacket) tokenRequest).getToken();
    }
}
