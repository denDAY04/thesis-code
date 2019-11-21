package edu.dk.asj.dpm.network.connections;

import edu.dk.asj.dpm.network.packets.Packet;
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
public class ClientConnection extends SAEConnection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnection.class);

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT_SEC = 1L;

    private final SocketAddress destination;
    private Packet request;

    private Packet response;
    private String error;
    private boolean requireResponse;
    private boolean finished;

    private ClientConnection(SocketAddress destination, UUID nodeId) {
        super("connection" + destination.toString(), nodeId, true);
        this.destination = destination;
        this.requireResponse = true;
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

    /**
     * Start the connection and its execution flow.
     */
    @Override
    public synchronized void start() {
        LOGGER.debug("Started client connection");
        if (request == null) {
            throw new IllegalStateException("Connection does not have a request");
        }
        super.start();
    }

    /**
     * <b>DO NOT</b> call this method. It is invoked when the connection is started.
     */
    @Override
    public void run() {
        super.run();

        if (!openConnection()) {
            cleanUp();
            return;
        }

        if (!saeHandshake()) {
            LOGGER.warn("SAE handshake failed");
            error = "Could not authenticate connection";
            cleanUp();
            return;
        }
        LOGGER.info("Established secure connection");

        if (!sendRequest()) {
            cleanUp();
            return;
        }

        if (requireResponse) {
            receiveResponse();
        }
        cleanUp();
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
     * Close the connection and clean up.
     */
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

        ByteBuffer requestBuffer = null;
        try {
            requestBuffer = ByteBuffer.wrap(encrypt(request.serialize()));
        } catch (Exception e) {
            LOGGER.warn("Exception caught while encrypting data", e);
            error = "Could not encrypt data";
        }
        Future<Integer> sendPromise = connection.write(requestBuffer);

        try {
            sendPromise.get(TIMEOUT_SEC, TimeUnit.SECONDS);
            LOGGER.debug("Request sent");
            return true;

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while sending request");
            error = "Interrupted while sending request";
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while sending request", e);
            error = "Unknown error while sending request";
        } catch (TimeoutException e) {
            LOGGER.warn("Send timed out");
            sendPromise.cancel(true);
            error = "Timed out while sending request";
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
            response = Packet.deserialize(decrypt(BufferHelper.readAndClear(responseBuffer)));

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
        } catch (Exception e) {
            LOGGER.warn("Exception caught while decrypting data", e);
            error = "Could not decrypt secure data";
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
}
