package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.requests.Packet;

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
public class ClientNetworkConnection extends Thread implements AutoCloseable {

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 5L;

    private final SocketAddress destination;
    private final Packet request;
    private AsynchronousSocketChannel connection;

    private Packet response;
    private String error;


    private ClientNetworkConnection(SocketAddress destination, Packet request) {
        super("Client connection -> " + destination.toString());
        this.destination = destination;
        this.request = request;
    }

    /**
     * Prepare a client connection in a separate thread and start the thread, which opens the connection.
     * @param request the packet that the connection should send.
     * @param destination the destination to which the packet should be sent.
     * @return the initialized and started connection.
     */
    public static ClientNetworkConnection send(Packet request, SocketAddress destination) {
        Objects.requireNonNull(destination, "Destination must not be null");
        Objects.requireNonNull(request, "Request must not be null");

        ClientNetworkConnection connection = new ClientNetworkConnection(destination, request);
        connection.start();
        return connection;
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

        receiveResponse();
        cleanUp();
    }

    /**
     * Close and clean up the connection.
     */
    @Override
    public void close() {
        cleanUp();
    }

    private boolean openConnection() {
        System.out.println("Connecting to " + destination);
        try {
            connection = AsynchronousSocketChannel.open();
        } catch (IOException e) {
            error = "Could not open client connection to " + destination;
            return false;
        }

        Future<Void> promise = connection.connect(destination);

        try {
            promise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[C] Connected");
            return true;

        } catch (InterruptedException e) {
            error = "Was interrupted while connecting";
        } catch (ExecutionException e) {
            error = "Exception occurred while connecting ["+e.getLocalizedMessage()+"]";
        } catch (TimeoutException e) {
            error = "Connect timed out";
        }

        return false;
    }

    private boolean sendRequest() {
        System.out.println("[C] Sending request...");
        ByteBuffer requestBuffer = ByteBuffer.wrap(request.serialize());
        Future<Integer> sendPromise = connection.write(requestBuffer);

        try {
            sendPromise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[C] Request sent");
            return true;

        } catch (InterruptedException e) {
            error = "Was interrupted while sending request";
        } catch (ExecutionException e) {
            error = "Exception caught while sending: ["+e.getLocalizedMessage()+"]";
        } catch (TimeoutException e) {
            sendPromise.cancel(true);
            error = "Send operation timed out";
        }

        return false;
    }

    private boolean receiveResponse() {
        System.out.println("[C] Receiving response...");
        ByteBuffer responseBuffer= ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> receivePromise = connection.read(responseBuffer);

        try {
            Integer readCount = receivePromise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[C] Received response");
            byte[] data = new byte[readCount];
            responseBuffer.flip().get(data);
            response = Packet.deserialize(data);
            return true;

        } catch (InterruptedException e) {
            error = "Was interrupted while receiving response";
        } catch (ExecutionException e) {
            error = "Exception caught while receiving: ["+e.getLocalizedMessage()+"]";
        } catch (TimeoutException e) {
            error = "Send operation timed out";
        }

        return false;
    }

    private void cleanUp() {
        System.out.println("[C] Cleaning up");
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                error = "CRITICAL ERROR - Failed to close client channel [" + e.getLocalizedMessage() +"]";
            }
        }
    }
}
