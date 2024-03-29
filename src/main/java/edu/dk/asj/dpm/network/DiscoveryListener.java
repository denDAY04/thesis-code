package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.connections.ClientConnection;
import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryHandler;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.util.BufferHelper;
import edu.dk.asj.dpm.util.NetworkInterfaceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DiscoveryListener extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryListener.class);

    private static final int BUFFER_CAPACITY = 1000;

    private static final long DISCOVERY_TIME_MS = 1000;
    private static final long DISCOVERY_IDLE_MS = 10;

    private static final String PEER_GROUP_ADDRESS = "232.0.0.0";
    private static final int PEER_GROUP_PORT = 35587;
    private static final InetSocketAddress PEER_GROUP_SOCKET_ADDRESS = new InetSocketAddress(PEER_GROUP_ADDRESS, PEER_GROUP_PORT);

    private DatagramChannel channel;
    private final ByteBuffer discoveryBuffer;
    private final DiscoveryHandler packetHandler;
    private final BigInteger networkId;
    private final UUID nodeId;

    private boolean isListening;
    private boolean closed;

    private boolean isDiscovering;
    private ConcurrentLinkedDeque<ClientConnection> discoveredNodes;

    private DiscoveryListener(DiscoveryHandler packetHandler, BigInteger networkId, UUID nodeId) {
        super("discovery-listener");
        this.packetHandler = packetHandler;
        this.networkId = networkId;
        this.nodeId = nodeId;
        discoveryBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        isListening = false;
        closed = false;
        isDiscovering = false;
        discoveredNodes = new ConcurrentLinkedDeque<>();

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
     * @param properties this node's network properties.
     * @return the listener.
     */
    public static DiscoveryListener open(DiscoveryHandler handler, NetworkProperties properties) {
        Objects.requireNonNull(handler, "Handler may not be null");
        Objects.requireNonNull(properties, "Network properties must not be null");

        DiscoveryListener listener = new DiscoveryListener(handler, properties.getNetworkId(), properties.getNodeId());
        listener.start();
        LOGGER.debug("Started node discovery listener for network ID {}", properties.getNetworkId());
        return listener;
    }

    /**
     * Execute the listener's flow. This is a continuous flow that does not end unless {@link DiscoveryListener#close()}
     * is called or an error was throw during network discovery or responding to network discovery from other nodes.
     */
    @Override
    public void run() {
        super.run();

        boolean threwError = false;
        do {
            if (isDiscovering) {
                try {
                    discoverNodes();
                } catch (IOException e) {
                    LOGGER.error("Exception while discovering network", e);
                    threwError = true;
                }
            } else if (isListening) {
                threwError = listenForDiscoveries();
            }
            try {
                sleep(DISCOVERY_IDLE_MS);
            } catch (InterruptedException e) {
                // do nothing
            }
        } while (!threwError && !closed);

        cleanUp();
    }

    /**
     * Enable the broadcast connection to listen for discovery requests from the network.
     */
    public synchronized void startListening() {
        LOGGER.debug("Started listening for discovery requests");
        isListening = true;
    }

    /**
     * Signal for the listener to initiate a new network discovery. This may not initiate immediately upon this method
     * returning, but it will happen as soon as possible.
     */
    public void startNetworkDiscovery() {
        isDiscovering = true;
    }

    /**
     * Check if the listener is currently in the process of doing network discovery.
     * @return true if the listener is currently in the process of discovering the network; false otherwise.
     */
    public boolean isDiscovering() {
        return isDiscovering;
    }

    /**
     * Poll the node connections queue to return the next connection that was discovered.
     * @return the next discovered connection in the queue, or null if no new connection has been discovered since the
     * last poll.
     */
    public ClientConnection getNextNodeConnection() {
        return discoveredNodes.poll();
    }

    /**
     * Close the listener and clean up.
     */
    @Override
    public synchronized void close() {
        closed = true;
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
            NetworkInterface nic = NetworkInterfaceHelper.getNetworkInterfaceController();
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
                    Packet response = packetHandler.process((DiscoveryPacket) request);
                    if (response != null) {
                        ByteBuffer responseBuffer = ByteBuffer.wrap(response.serialize());
                        try {
                            LOGGER.debug("Sending discovery response {} to {}", response, sender);
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

    private synchronized void discoverNodes() throws IOException {
        LOGGER.info("Discovering network nodes");

        // send discovery packet to network
        DiscoveryPacket packet = new DiscoveryPacket(networkId);
        ByteBuffer sendBuffer = ByteBuffer.wrap(packet.serialize());
        try {
            LOGGER.debug("Sending request {} to {}", packet, PEER_GROUP_SOCKET_ADDRESS);
            channel.send(sendBuffer, PEER_GROUP_SOCKET_ADDRESS);
        } catch (IOException e) {
            isDiscovering = false;
            throw new IOException("Cloud not send discovery request", e);
        }

        // listen for echo replies and establish connections
        ByteBuffer receiveBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        long discoveryEndTime = System.currentTimeMillis() + DISCOVERY_TIME_MS;
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
                    discoveredNodes.offer(ClientConnection.prepare(discoveredNodeAddress, nodeId));
                } else {
                    LOGGER.warn("Unexpected discovery response {}", response);
                }
            } else {
                try {
                    sleep(DISCOVERY_IDLE_MS);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
        isDiscovering = false;
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

        LOGGER.debug("Discovery request Is valid");
        return true;
    }
}
