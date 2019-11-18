package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.util.BufferHelper;
import edu.dk.asj.dpm.util.NetworkInterfaceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class DiscoveryListener extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryListener.class);
    private static final int BUFFER_CAPACITY = 1000;
    private static final long DISCOVERY_TIMEOUT_MS = 1000 * 5;
    private static final long IDLE_SLEEP_MS = 50;
    private static final String PEER_GROUP_ADDRESS = "232.0.0.0";
    private static final int PEER_GROUP_PORT = 35587;

    private DatagramChannel channel;
    private final DiscoveryHandler packetHandler;
    private final BigInteger networkId;
    private final ByteBuffer discoveryBuffer;
    private final InetSocketAddress networkSocketAddress;
    private boolean isListening;

    private DiscoveryListener(DiscoveryHandler packetHandler, BigInteger networkId) {
        super("discovery-listener");
        this.packetHandler = packetHandler;
        this.networkId = networkId;
        discoveryBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        isListening = false;
        networkSocketAddress = new InetSocketAddress(PEER_GROUP_ADDRESS, PEER_GROUP_PORT);

        if (!openConnection()) {
            cleanUp();
            throw new RuntimeException("Discovery listener encountered an error");
        }
    }

    /**
     * Construct and start a node discovery listener. The started listener is available for sending requests to the
     * network, but it will not listen for discovery requests before {@link DiscoveryListener#startListening()} is
     * called.
     * @param handler the object that should handle discovery requests.
     * @param networkId the ID of this node's network. The listener will filter out discovery requests that are not
     *                  addressed to this network ID.
     * @return the listener.
     */
    public static DiscoveryListener open(DiscoveryHandler handler, BigInteger networkId) {
        Objects.requireNonNull(handler, "Handler may not be null");
        Objects.requireNonNull(networkId, "Network ID must not be null");

        DiscoveryListener listener = new DiscoveryListener(handler, networkId);
        listener.start();
        LOGGER.debug("Started node discovery listener for network ID {}", networkId);
        return listener;
    }

    @Override
    public void run() {
        super.run();

        boolean threwError = false;
        do {
            if (isListening) {
                threwError = listenForDiscoveries();
            }
            try {
                sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                // do nothing
            }
        } while (!threwError);

        cleanUp();
    }

    public synchronized Deque<ClientConnection> getNetworkConnections() throws IOException {
        LOGGER.info("Getting network connections");

        // send discovery packet to network
        DiscoveryPacket packet = new DiscoveryPacket(networkId);
        ByteBuffer sendBuffer = ByteBuffer.wrap(packet.serialize());
        try {
            LOGGER.debug("Sending request {} to {}", packet, networkSocketAddress);
            channel.send(sendBuffer, networkSocketAddress);
        } catch (IOException e) {
            LOGGER.error("Could not send request", e);
            throw new IOException("Cloud not discover network - sending request");
        }

        // listen for echo replies and establish connections
        Deque<ClientConnection> connections = new ArrayDeque<>();
        ByteBuffer receiveBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        long discoveryEndTime = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MS;
        LOGGER.debug("Waiting for responses");
        while (System.currentTimeMillis() < discoveryEndTime) {
            SocketAddress sender = channel.receive(receiveBuffer);
            if (sender != null) {
                Packet response = Packet.deserialize(BufferHelper.readAndClear(receiveBuffer));
                if (response instanceof DiscoveryEchoPacket) {
                    InetSocketAddress discoveredNodeAddress = new InetSocketAddress(
                            ((InetSocketAddress) sender).getAddress(),
                            ((DiscoveryEchoPacket) response).getConnectionPort());
                    LOGGER.debug("Discovered node connection {}", discoveredNodeAddress);
                    connections.offer(ClientConnection.prepare(discoveredNodeAddress));
                } else {
                    LOGGER.warn("Unexpected discovery response {}", response);
                }
            } else {
                try {
                    sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
        return connections;
    }

    /**
     * Enable the broadcast connection to listen for discovery requests from the network.
     */
    public synchronized void startListening() {
        LOGGER.debug("Started listening for discovery requests");
        isListening = true;
    }

    @Override
    public void close() {
        cleanUp();
    }

    private void cleanUp() {
        LOGGER.info("Cleaning up");
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close channel", e);
                packetHandler.error("Clean-up error for node discovery listener.");
            }
        }
    }

    private boolean openConnection() {
        LOGGER.debug("Opening channel");
        try {
            NetworkInterface nic = NetworkInterfaceHelper.getActiveNetInterface();
            if (nic == null) {
                LOGGER.error("Could not determine a valid network interface");
                return false;
            }

            channel = DatagramChannel.open(StandardProtocolFamily.INET);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .bind(new InetSocketAddress(PEER_GROUP_PORT))
                    .setOption(StandardSocketOptions.IP_MULTICAST_IF, nic)
                    .setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false);
            channel.join(InetAddress.getByName(PEER_GROUP_ADDRESS), nic);
            channel.configureBlocking(false);

            return true;
        } catch (IOException e) {
            LOGGER.error("Could not open channel", e);
            return false;
        }
    }

    private synchronized boolean listenForDiscoveries() {
        SocketAddress sender;
        boolean hasError = false;

        try {
            sender = channel.receive(discoveryBuffer);
            if (sender != null) {
                LOGGER.debug("Received request from " + sender);
                Packet request = Packet.deserialize(BufferHelper.readAndClear(discoveryBuffer));

                if (isValidRequest(request)) {
                    Packet response = packetHandler.process((DiscoveryPacket) request, sender);
                    if (response != null) {
                        ByteBuffer responseBuffer = ByteBuffer.wrap(response.serialize());
                        try {
                            channel.send(responseBuffer, sender);
                        } catch (IOException e) {
                            LOGGER.warn("Unexpected exception while sending response", e);
                            packetHandler.error("An error occurred while sending discovery response");
                            hasError = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected exception while receiving request", e);
            packetHandler.error("An error occurred while listening for discovery requests");
            hasError = true;
        }
        return hasError;
    }

    private boolean isValidRequest(Packet request) {
        if (!(request instanceof DiscoveryPacket)) {
            LOGGER.debug("Ignoring invalid request packet.");
            return false;
        }
        DiscoveryPacket discoveryRequest = (DiscoveryPacket) request;
        if (!networkId.equals(discoveryRequest.getNetworkId())) {
            LOGGER.debug("Ignoring discovery request from incorrect network ID {}", discoveryRequest.getNetworkId());
            return false;
        }
        return true;
    }
}
