package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.IdentityPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.network.packets.SAEParameterPacket;
import edu.dk.asj.dpm.network.packets.SAETokenPacket;
import edu.dk.asj.dpm.security.SAEParameterSpec;
import edu.dk.asj.dpm.security.SAESession;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.util.BufferHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client stream-oriented connection running in its own isolated thread.
 */
public class ClientConnection extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnection.class);
    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT_SEC = 1L;
    private static final long SAE_HANDSHAKE_TIMEOUT_MS = 5000;
    private static final int SAE_BUFFER_CAPACITY = 1000;

    private final SocketAddress destination;
    private final UUID nodeId;
    private Packet request;
    private AsynchronousSocketChannel connection;

    private Packet response;
    private String error;
    private boolean requireResponse;
    private boolean finished;


    private ClientConnection(SocketAddress destination, UUID nodeId) {
        super("connection" + destination.toString());
        this.destination = destination;
        this.requireResponse = true;
        this.nodeId = nodeId;
    }

    /**
     * Construct a client connection in a separate thread. This thread is not yet started. In order to start the thread
     * you must first set the connection's request using {@link ClientConnection#setRequest(Packet,boolean)} and then
     * start the connection using {@link ClientConnection#start()}.
     * @param destination the destination of the connection.
     * @param nodeId the identity of this node.
     * @return the initialized (but not started) connection.
     */
    public static ClientConnection prepare(SocketAddress destination, UUID nodeId) {
        Objects.requireNonNull(destination, "Destination must not be null");
        return new ClientConnection(destination, nodeId);
    }

    @Override
    public synchronized void start() {
        LOGGER.debug("Started client connection");
        if (request == null) {
            throw new IllegalStateException("Connection does not have a request");
        }
        super.start();
    }

    /**
     * Set the request to be sent with the connection.
     * @param request the request packet to be sent to the connection destination.
     * @param requireResponse flag for whether the connection should wait for a response to the request.
     */
    public void setRequest(Packet request, boolean requireResponse) {
        Objects.requireNonNull(request);
        this.request = request;
        this.requireResponse = requireResponse;
    }

    /**
     * Get the response received by this connection, in response to its request.
     * @return the response. Will be null if an error occurred prior to receiving the response.
     */
    public Packet getResponse() {
        return response;
    }

    /**
     * Get the error message, if any, that occurred during this connection's lifetime.
     * @return the error message. Will be null if no error has occurred.
     */
    public String getError() {
        return error;
    }

    /**
     * Determine if the connection has finished its flow.
     * @return true if the connection flow has finished; false otherwise.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Execute the flow of the connection and its actions of sending its request and receiving its response.
     */
    @Override
    public void run() {
        super.run();

        if (!openConnection()) {
            cleanUp();
            return;
        }

        byte[] encryptionKey = saeHandshake();
        if (encryptionKey == null) {
            LOGGER.warn("SAE handshake failed");
            error = "Could not authenticate connection";
            cleanUp();
            return;
        }
        LOGGER.debug("Established secure connection");

        // todo ensure encryption on subsequent data

        if (!sendRequest()) {
            cleanUp();
            return;
        }

        if (requireResponse) {
            receiveResponse();
        }
        cleanUp();
    }

    @Override
    public void close() {
        cleanUp();
    }

    private boolean openConnection() {
        LOGGER.debug("Connecting to " + destination);
        try {
            connection = AsynchronousSocketChannel.open();
        } catch (IOException e) {
            LOGGER.error("Could not open client connection", e);
            error = "Failed to open client connection";
            return false;
        }

        Future<Void> promise = connection.connect(destination);

        try {
            promise.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            LOGGER.debug("Connected");
            return true;

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while connecting to node");
            error = "An error occurred while connecting to node";
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while connecting to node", e);
            error = "An error occurred while connecting to node";
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while connecting to node");
            promise.cancel(true);
            error = "Connect timed out";
        }

        return false;
    }

    private boolean sendRequest() {
        LOGGER.debug("Sending request");
        ByteBuffer requestBuffer = ByteBuffer.wrap(request.serialize());
        Future<Integer> sendPromise = connection.write(requestBuffer);

        try {
            sendPromise.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            LOGGER.debug("Request sent");
            return true;

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending request");
            error = "An error occurred while sending request";
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while sending request", e);
            error = "An error occurred while sending request";
        } catch (TimeoutException e) {
            LOGGER.warn("Send timed out");
            sendPromise.cancel(true);
            error = "Send request timed out";
        }

        return false;
    }

    private void receiveResponse() {
        LOGGER.debug("Receiving response");
        ByteBuffer responseBuffer= ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> receivePromise = connection.read(responseBuffer);

        try {
            receivePromise.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            LOGGER.debug("Received response");
            response = Packet.deserialize(BufferHelper.readAndClear(responseBuffer));

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving response");
            error = "An error occurred while receiving a node response";
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while receiving response", e);
            error = "An error occurred while receiving a node response";
        } catch (TimeoutException e) {
            LOGGER.warn("Receive timed out");
            receivePromise.cancel(true);
            error = "No response";
        }
    }

    private void cleanUp() {
        LOGGER.info("Cleaning up");
        finished = true;
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up connection", e);
                error = "Clean-up failed";
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
        Packet identityRequest = new IdentityPacket(nodeId);
        Future<Integer> sendIdPromise = connection.write(ByteBuffer.wrap(identityRequest.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE identity");
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

        ByteBuffer idBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveIdPromise = connection.read(idBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE identity");
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

        Packet response = Packet.deserialize(BufferHelper.readAndClear(idBuffer));
        if (!(response instanceof IdentityPacket)) {
            LOGGER.warn("Received invalid SAE identity");
            return null;
        }
        return ((IdentityPacket) response).getNodeId();
    }

    private SAEParameterSpec exchangeSAEParameters(SAEParameterSpec parameters) {
        Packet request = new SAEParameterPacket(parameters);
        Future<Integer> sendParamPromise = connection.write(ByteBuffer.wrap(request.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE parameters");
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

        ByteBuffer parameterBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveParamPromise = connection.read(parameterBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE parameters");
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

        Packet response = Packet.deserialize(BufferHelper.readAndClear(parameterBuffer));
        if (!(response instanceof SAEParameterPacket)) {
            LOGGER.warn("Received invalid SAE parameter response");
            return null;
        }
        return ((SAEParameterPacket) response).getParameters();
    }

    private byte[] exchangeSAETokens(byte[] token) {
        Packet request = new SAETokenPacket(token);
        Future<Integer> sendTokenPromise = connection.write(ByteBuffer.wrap(request.serialize()));
        int sentBytes = -1;
        try {
            LOGGER.debug("Sending SAE token");
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

        ByteBuffer tokenBuffer = ByteBuffer.allocate(SAE_BUFFER_CAPACITY);
        Future<Integer> receiveTokenPromise = connection.read(tokenBuffer);
        int receivedBytes = -1;
        try {
            LOGGER.debug("Receiving SAE token");
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

        Packet response = Packet.deserialize(BufferHelper.readAndClear(tokenBuffer));
        if (!(response instanceof SAETokenPacket)) {
            LOGGER.warn("Received invalid SAE token response");
            return null;
        }
        return ((SAETokenPacket) response).getToken();
    }
}
