package edu.dk.asj.dpm.network;


import edu.dk.asj.dpm.network.requests.Packet;

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

public class ServerNetworkConnection extends Thread implements AutoCloseable {

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 5L;

    private AsynchronousServerSocketChannel server;
    private AsynchronousSocketChannel connection;
    private RequestHandler requestHandler;
    private int port;

    private ServerNetworkConnection(RequestHandler requestHandler, int port) {
        super("Server connection");
        this.requestHandler = requestHandler;
        this.port = port;
    }

    public static ServerNetworkConnection open(RequestHandler requestHandler, int port) {
        Objects.requireNonNull(requestHandler, "Request processor must not be null");

        ServerNetworkConnection connection = new ServerNetworkConnection(requestHandler, port);
        connection.start();
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
            requestHandler.error("Failed to open server socket ["+e.getLocalizedMessage()+"]");
        }
        return false;
    }

    private boolean acceptConnection() {
        System.out.println("[S] Waiting for connections...");
        Future<AsynchronousSocketChannel> acceptPromise = server.accept();

        try {
            connection = acceptPromise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[S] Accepted connection");
            return true;

        } catch (InterruptedException e) {
            requestHandler.error("Was interrupted while waiting for connection");
        } catch (ExecutionException e) {
            requestHandler.error("Exception caught while waiting: ["+e.getLocalizedMessage()+"]");
        } catch (TimeoutException e) {
            acceptPromise.cancel(true);
            requestHandler.error("Accept operation timed out");
        }

        return false;
    }

    private Packet receiveRequest() {
        System.out.println("[S] Receiving packet...");
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> promise = connection.read(buffer);

        try {
            Integer readCount = promise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[S] Received packet");
            byte[] data = new byte[readCount];
            buffer.flip().get(data);
            return Packet.deserialize(data);

        } catch (InterruptedException e) {
            requestHandler.error("Interrupted while receiving request");
        } catch (ExecutionException e) {
            requestHandler.error("Exception occurred while receiving request ["+e.getLocalizedMessage()+"]");
        } catch (TimeoutException e) {
            promise.cancel(true);
            requestHandler.error("Receive timed out");
        }

        return null;
    }

    private void sendResponse(Packet response) {
        System.out.println("[S] Sending response...");
        ByteBuffer buffer = ByteBuffer.wrap(response.serialize());
        Future<Integer> promise = connection.write(buffer);

        try {
            promise.get(TIMEOUT, TimeUnit.SECONDS);
            System.out.println("[S] Response sent");
        } catch (InterruptedException e) {
            requestHandler.error("Was interrupted while sending response");
        } catch (ExecutionException e) {
            requestHandler.error("Exception occurred while sending response ["+e.getLocalizedMessage()+"]");
        } catch (TimeoutException e) {
            promise.cancel(true);
            requestHandler.error("Timed out while sending response");
        }
    }

    private void cleanUp() {
        System.out.println("[S] Cleaning up");
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                requestHandler.error("CRITICAL ERROR - Could not clean up connection ["+e.getLocalizedMessage()+"]");
            }
        }

        if (server != null && server.isOpen()) {
            try {
                server.close();
            } catch (IOException e) {
                requestHandler.error("CRITICAL ERROR - Could not clean up server ["+e.getLocalizedMessage()+"]");
            }
        }
    }
}
