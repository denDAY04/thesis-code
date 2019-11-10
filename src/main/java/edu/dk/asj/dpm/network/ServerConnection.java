package edu.dk.asj.dpm.network;


import edu.dk.asj.dpm.network.requests.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerConnection extends Thread implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnection.class);

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 5L;

    private AsynchronousServerSocketChannel server;
    private AsynchronousSocketChannel connection;
    private RequestHandler requestHandler;
    private int port;

    private ServerConnection(RequestHandler requestHandler, int port) {
        super("Server connection p:"+port);
        this.requestHandler = requestHandler;
        this.port = port;
    }

    public static ServerConnection open(RequestHandler requestHandler, int port) {
        Objects.requireNonNull(requestHandler, "Request processor must not be null");

        ServerConnection connection = new ServerConnection(requestHandler, port);
        connection.start();

        LOGGER.info("Started server connection");
        return connection;
    }

    @Override
    public void run() {
        super.run();

        if (!openConnection()) {
            cleanUp();
            return;
        }

        if (!acceptConnection()) {
            cleanUp();;
            return;
        }

        Packet request = receiveRequest();
        if (request == null) {
            cleanUp();;
            return;
        }

        Packet response = requestHandler.process(request);
        if (response != null) {
            sendResponse(response);
        }

        cleanUp();
    }

    @Override
    public void close() {
        cleanUp();
    }

    private boolean openConnection() {
        try {
            server = AsynchronousServerSocketChannel.open();
            server.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not bind server socket", e);
            requestHandler.error("Failed to open server connection");
        }
        return false;
    }

    private boolean acceptConnection() {
        LOGGER.debug("Waiting for connections");
        Future<AsynchronousSocketChannel> acceptPromise = server.accept();

        try {
            connection = acceptPromise.get(TIMEOUT, TimeUnit.SECONDS);
            LOGGER.debug("Accepted connection");
            return true;

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for request");
            requestHandler.error("An error occurred while waiting for a request");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while waiting for request", e);
            requestHandler.error("An error occurred while waiting for a request");
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while waiting for request");
            acceptPromise.cancel(true);
            requestHandler.error("No requests");
        }

        return false;
    }

    private Packet receiveRequest() {
        LOGGER.debug("Receiving packet");
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> promise = connection.read(buffer);

        try {
            Integer readCount = promise.get(TIMEOUT, TimeUnit.SECONDS);
            LOGGER.debug("Received packet");
            byte[] data = new byte[readCount];
            buffer.flip().get(data);
            return Packet.deserialize(data);

        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while receiving request");
            requestHandler.error("An error occurred while receiving a node response");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception during receive", e);
            requestHandler.error("An error occurred while receiving a node response");
        } catch (TimeoutException e) {
            LOGGER.warn("Receive timed out");
            promise.cancel(true);
            requestHandler.error("No response");
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
            requestHandler.error("An error occurred while sending response");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while sending response", e);
            requestHandler.error("An error occurred while sending response");
        } catch (TimeoutException e) {
            LOGGER.warn("Send timed out");
            promise.cancel(true);
            requestHandler.error("Could not send response");
        }
    }

    private void cleanUp() {
        LOGGER.info("Cleaning up");
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up connection", e);
                requestHandler.error("Clean-up failed");
            }
        }

        if (server != null && server.isOpen()) {
            try {
                server.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up server", e);
                requestHandler.error("Clean-up failed");
            }
        }
    }
}
