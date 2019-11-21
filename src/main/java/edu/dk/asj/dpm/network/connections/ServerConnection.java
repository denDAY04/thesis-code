package edu.dk.asj.dpm.network.connections;

import edu.dk.asj.dpm.network.packets.PacketHandler;
import edu.dk.asj.dpm.network.packets.Packet;
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

/**
 * Server stream-oriented connection running in its own isolated thread.
 */
public class ServerConnection extends SAEConnection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnection.class);

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 10L;

    private final AsynchronousServerSocketChannel connectionListener;
    private final PacketHandler packetHandler;
    private final int port;

    private ServerConnection(PacketHandler packetHandler, UUID nodeId) {
        super(nodeId, false);
        this.packetHandler = packetHandler;
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

    /**
     * Open and start a new server connection running in its own thread.
     * @param packetHandler the handler that will be served the packets received by the connection.
     * @param nodeId this node's ID.
     * @return the started connection.
     */
    public static ServerConnection open(PacketHandler packetHandler, UUID nodeId) {
        Objects.requireNonNull(packetHandler, "Request processor must not be null");
        Objects.requireNonNull(nodeId, "Node identity must not be null");

        ServerConnection connection = new ServerConnection(packetHandler, nodeId);
        connection.start();

        LOGGER.info("Started server connection");
        return connection;
    }

    /**
     * <b>DO NOT</b> call this method. It is invoked when the connection is opened during construction.
     */
    @Override
    public void run() {
        if (!acceptConnection()) {
            cleanUp();;
            return;
        }

        if (!saeHandshake()) {
            LOGGER.warn("SAE handshake failed");
            packetHandler.error("Could not authenticate connection");
            cleanUp();
            return;
        }
        LOGGER.debug("Established secure connection");

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

    /**
     * Close the connection and clean up.
     */
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
            return Packet.deserialize(decrypt(BufferHelper.readAndClear(buffer)));

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

        } catch (Exception e) {
            LOGGER.warn("Exception caught while decrypting data", e);
            packetHandler.error("Could not decrypt secure data");
        }

        return null;
    }

    private void sendResponse(Packet response) {
        LOGGER.debug("Sending response");
        ByteBuffer buffer = null;
        try {
            buffer = ByteBuffer.wrap(encrypt(response.serialize()));
        } catch (Exception e) {
            LOGGER.warn("Exception caught while encrypting data", e);
            packetHandler.error("Could not encrypt data");
        }
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
}
