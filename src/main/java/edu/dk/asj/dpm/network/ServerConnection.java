package edu.dk.asj.dpm.network;


import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.util.BufferHelper;
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
    private PacketHandler packetHandler;
    private int port;

    private ServerConnection(PacketHandler packetHandler, int port) {
        super("Server connection p:"+port);
        this.packetHandler = packetHandler;
        this.port = port;
    }

    public static ServerConnection open(PacketHandler packetHandler, int port) {
        Objects.requireNonNull(packetHandler, "Request processor must not be null");

        ServerConnection connection = new ServerConnection(packetHandler, port);
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

        Packet response = packetHandler.process(request);
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
            packetHandler.error("Failed to open server connection");
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
            packetHandler.error("An error occurred while waiting for a server request");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while waiting for request", e);
            packetHandler.error("An error occurred while waiting for a server request");
        } catch (TimeoutException e) {
            LOGGER.warn("Timed out while waiting for request");
            acceptPromise.cancel(true);
            packetHandler.error("No requests to server");
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
            packetHandler.error("An error occurred while receiving a node response");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception during receive", e);
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
            packetHandler.error("An error occurred while sending server response");
        } catch (ExecutionException e) {
            LOGGER.warn("Unknown exception while sending response", e);
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
                packetHandler.error("Clean-up failed for server connection");
            }
        }

        if (server != null && server.isOpen()) {
            try {
                server.close();
            } catch (IOException e) {
                LOGGER.error("Failed to clean up server", e);
                packetHandler.error("Clean-up failed for server connection");
            }
        }
    }
}
