package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.Packet;
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

public class DiscoveryListener extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryListener.class);
    private static final int BUFFER_CAPACITY = 1000;

    static final String PEER_GROUP_ADDRESS = "232.0.0.0";
    static final int PEER_GROUP_PORT = 35587;

    private DatagramChannel channel;
    private final PacketHandler<DiscoveryPacket> packetHandler;
    private final BigInteger networkId;

    private DiscoveryListener(PacketHandler<DiscoveryPacket> packetHandler, BigInteger networkId) {
        super("discovery-listener");
        this.packetHandler = packetHandler;
        this.networkId = networkId;
    }

    /**
     * Construct and start a node discovery listener.
     * @param handler the object that should handle discovery requests.
     * @param networkId the ID of this node's network. The listener will filter out discovery requests that are not
     *                  addressed to this network ID.
     * @return the listener.
     */
    public static DiscoveryListener start(PacketHandler<DiscoveryPacket> handler, BigInteger networkId) {
        Objects.requireNonNull(handler, "Handler may not be null");
        Objects.requireNonNull(networkId, "Network ID must not be null");

        DiscoveryListener listener = new DiscoveryListener(handler, networkId);
        listener.start();
        LOGGER.info("Started node discovery listener for network ID {}", networkId);
        return listener;
    }

    @Override
    public void run() {
        super.run();

        if (!openConnection()) {
            cleanUp();
            return;
        }

        listen();
        cleanUp();
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
            channel = DatagramChannel.open(StandardProtocolFamily.INET);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .bind(new InetSocketAddress(PEER_GROUP_PORT))
                    .setOption(StandardSocketOptions.IP_MULTICAST_IF, nic)
                    .setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false);
            channel.join(InetAddress.getByName(PEER_GROUP_ADDRESS), nic);

            return true;
        } catch (IOException e) {
            LOGGER.error("Could not open channel", e);
            packetHandler.error("Failed to open node discovery listener");
            return false;
        }
    }

    private void listen() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        SocketAddress sender;

        while (true) {
            LOGGER.debug("Listening for requests");
            try {
                sender = channel.receive(buffer);
                LOGGER.debug("Received request from " + sender);
            } catch (IOException e) {
                LOGGER.error("Unexpected exception while receiving request", e);
                packetHandler.error("An error occurred while listening for discovery requests");
                return;
            }

            Packet request = Packet.deserialize(BufferHelper.readAndClear(buffer));
            if (!isValidRequest(request)) {
                continue;
            };

            Packet response = packetHandler.process((DiscoveryPacket) request, sender);
            if (response != null) {
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.serialize());
                try {
                    channel.send(responseBuffer, sender);
                } catch (IOException e) {
                    LOGGER.warn("Unexpected exception while sending response", e);
                    packetHandler.error("An error occurred while sending discovery response");
                    return;
                }
            }
        }
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
