package dk.asj.dpm.network;

import dk.asj.dpm.network.requests.Request;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientNetworkConnection extends Thread implements AutoCloseable {

    private static final int BUFFER_CAPACITY = 10 * 1000 * 1000;
    private static final long TIMEOUT = 5L;

    private final SocketAddress destination;
    private final Request request;
    private final RequestProcessor processor;
    private AsynchronousSocketChannel channel;


    private ClientNetworkConnection(SocketAddress destination, Request request, RequestProcessor processor) {
        super("Client channel -> " + destination.toString());
        this.destination = destination;
        this.request = request;
        this.processor = processor;
    }


    @Override
    public void run() {
        super.run();

        try {
            channel = AsynchronousSocketChannel.open();
            channel.connect(destination);
        } catch (IOException e) {
            System.err.println("Could not open client connection to " + destination);
            cleanUp();
            return;
        }

        // TODO authenticate connection

        // Send request
        ByteBuffer requestBuffer = ByteBuffer.wrap(request.serialize());
        Future<Integer> sendPromise = channel.write(requestBuffer);
        boolean sendFailed = true;
        try {
            sendPromise.get(TIMEOUT, TimeUnit.SECONDS);
            sendFailed = false;
        } catch (InterruptedException e) {
            processor.error("Was interrupted while sending request");
        } catch (ExecutionException e) {
            processor.error("Exception caught while sending: ["+e.getLocalizedMessage()+"]");
        } catch (TimeoutException e) {
            processor.error("Send operation timed out");
        }
        if (sendFailed) {
            cleanUp();
            return;
        }

        // Receive response
        ByteBuffer responseBuffer= ByteBuffer.allocate(BUFFER_CAPACITY);
        Future<Integer> receivePromise = channel.read(responseBuffer);

        Integer readCount = 0;
        boolean receiveFailed = true;
        try {
            readCount = receivePromise.get(TIMEOUT, TimeUnit.SECONDS);
            receiveFailed = false;
        } catch (InterruptedException e) {
            processor.error("Was interrupted while receiving response");
        } catch (ExecutionException e) {
            processor.error("Exception caught while receiving: ["+e.getLocalizedMessage()+"]");
        } catch (TimeoutException e) {
            processor.error("Send operation timed out");
        }
        if (receiveFailed) {
            cleanUp();
            return;
        }

        responseBuffer.flip();
        byte[] data = new byte[readCount];
        responseBuffer.get(data);
        processor.process(Request.deserialize(data));

        cleanUp();
    }


    @Override
    public void close() throws Exception {
        cleanUp();
    }

    private void cleanUp() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("CRITICAL ERROR - Failed to close client channel [" + e.getLocalizedMessage() +"]");
            }
        }
    }

    /**
     * Prepare a client connection in a separate thread and start the thread, which opens the connection.
     * @param destination
     * @param request
     * @param processor
     * @return
     */
    public static ClientNetworkConnection send(SocketAddress destination, Request request, RequestProcessor processor) {
        Objects.requireNonNull(destination, "Destination must not be null");
        Objects.requireNonNull(processor, "Request processor must not be null");

        ClientNetworkConnection connection = new ClientNetworkConnection(destination, request, processor);
        connection.start();
        return connection;
    }
}
