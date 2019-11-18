package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.util.BufferHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
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
    private static final long TIMEOUT = 5L;

    private final SocketAddress destination;
    private Packet request;
    private AsynchronousSocketChannel connection;

    private Packet response;
    private String error;
    private boolean requireResponse;
    private boolean finished;


    private ClientConnection(SocketAddress destination, Packet request, boolean requireResponse) {
        super("connection" + destination.toString());
        this.destination = destination;
        this.request = request;
        this.requireResponse = requireResponse;
    }

    /**
     * Prepare a client connection in a separate thread and start the thread, which opens the connection.
     * @param request the packet that the connection should send.
     * @param destination the destination to which the packet should be sent.
     * @param requireResponse flag indicating whether the connection must wait for a response.
     * @return the initialized and started connection.
     */
    public static ClientConnection send(Packet request, SocketAddress destination, boolean requireResponse) {
        Objects.requireNonNull(destination, "Destination must not be null");
        Objects.requireNonNull(request, "Request must not be null");

        ClientConnection connection = new ClientConnection(destination, request, requireResponse);
        connection.start();
        return connection;
    }

    /**
     * Construct a client connection in a separate thread. This thread is not yet started. In order to start the thread
     * you must first set the connection's request using {@link ClientConnection#setRequest(Packet,boolean)} and then
     * start the connection using {@link ClientConnection#start()}.
     * @param destination the destination of the connection.
     * @return the initialized (but not started) connection.
     */
    public static ClientConnection prepare(SocketAddress destination) {
        Objects.requireNonNull(destination, "Destination must not be null");
        return new ClientConnection(destination, null, true);
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
     * Set the request to be sent with the connection. This method should only be used on a connection that was
     * initialized using {@link ClientConnection#prepare(SocketAddress)}.
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

        // TODO authenticate connection

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
            promise.get(TIMEOUT, TimeUnit.SECONDS);
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
            sendPromise.get(TIMEOUT, TimeUnit.SECONDS);
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
            receivePromise.get(TIMEOUT, TimeUnit.SECONDS);
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
}
