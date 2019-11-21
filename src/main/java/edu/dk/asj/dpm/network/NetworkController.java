package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.connections.ClientConnection;
import edu.dk.asj.dpm.network.connections.ServerConnection;
import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryHandler;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.FragmentPacket;
import edu.dk.asj.dpm.network.packets.GetFragmentPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.network.packets.PacketHandler;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NetworkController implements DiscoveryHandler, PacketHandler, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkController.class);
    private static final long IDLE_MS = 50;

    private DiscoveryListener discoveryListener;
    private final PropertiesContainer propertiesContainer;
    private final BigInteger networkId;
    private final UUID nodeId;
    private int networkSize;

    public NetworkController(NetworkProperties properties, PropertiesContainer propertiesContainer) {
        networkId = properties.getNetworkId();
        nodeId = properties.getNodeId();
        this.propertiesContainer = propertiesContainer;
        discoveryListener = DiscoveryListener.open(this, properties);
    }

    /**
     * Start the network discovery listener, waiting for discovery requests from the network.
     */
    public void startDiscoveryListener() {
        discoveryListener.startListening();
    }

    /**
     * Get vault fragments from the node network.
     * @return the node network's fragments.
     * @throws IOException if an I/O error occurred.
     */
    public Collection<VaultFragment> getNetworkFragments() throws IOException {
        Deque<ClientConnection> nodeConnections = sendRequestToNetwork(new GetFragmentPacket(networkId), true);

        networkSize = 1;
        boolean hasError = false;
        List<VaultFragment> fragments = new ArrayList<>(nodeConnections.size());
        while (!nodeConnections.isEmpty()) {
            ClientConnection connection = getFinishedConnection(nodeConnections);
            if (connection.getResponse() != null) {
                if (connection.getResponse() instanceof FragmentPacket) {
                    networkSize++;
                    fragments.add(((FragmentPacket) connection.getResponse()).getFragment());
                    LOGGER.debug("Accepted fragment from {}", connection.getName());
                } else {
                    LOGGER.warn("Unexpected reply to network fragment request. Expected {} but was {}", FragmentPacket.class, connection.getClass());
                    hasError = true;
                }

            } else if (connection.getError() != null) {
                LOGGER.warn("Fragment request error: {}", connection.getError());
                hasError = true;
            } else {
                LOGGER.warn("{} finished with no response or error", connection.getName());
            }

            if (hasError) {
                nodeConnections.forEach(ClientConnection::close);
                throw new IOException("Failed to get network fragments");
            }
        }
        LOGGER.debug("Network size is {}", networkSize);
        return fragments;
    }

    /**
     * Send the new fragments to the node network.
     * @param fragments the new fragments.
     * @return true if the fragments were sent, false otherwise.
     */
    public boolean sendNetworkFragments(VaultFragment[] fragments) throws IOException {
        Objects.requireNonNull(fragments, "Fragments must not be null");
        LOGGER.debug("Sending fragments to network");

        Deque<Packet> fragmentRequests = new ArrayDeque<>(fragments.length);
        for (VaultFragment fragment : fragments) {
            fragmentRequests.offer(new FragmentPacket(fragment));
        }

        Deque<ClientConnection> connections = sendRequestsToNetwork(fragmentRequests, false);
        if (connections.isEmpty()) {
            LOGGER.warn("No network connections to send new fragments to");
            return false;
        }

        while (!connections.isEmpty()) {
            ClientConnection connection = getFinishedConnection(connections);
            if (connection.getError() != null) {
                LOGGER.warn("Send network fragments error: {}", connection.getError());
                connections.forEach(ClientConnection::close);
                return false;
            }
        }
        return true;
    }

    /**
     * Ge the size of the node network (including this host node)
     * @return the network size.
     */
    public int getNetworkSize() {
        return networkSize;
    }

    @Override
    public void close() {
        LOGGER.info("Closing network resources");
        discoveryListener.close();
    }

    @Override
    public DiscoveryEchoPacket process(DiscoveryPacket packet) {
        ServerConnection connection = ServerConnection.open(this, nodeId);
        return new DiscoveryEchoPacket(connection.getPort());
    }

    @Override
    public Packet process(Packet request) {
        if (request instanceof FragmentPacket) {
            VaultFragment fragment = ((FragmentPacket) request).getFragment();
            String path = propertiesContainer.getStorageProperties().getFragmentPath();
            boolean saved = SecurityController.getInstance().saveFragment(fragment, path);
            if (!saved) {
                error("Failed to save new fragment");
            }
            return null;

        } else if (request instanceof GetFragmentPacket) {
            if (!networkId.equals(((GetFragmentPacket) request).getNetworkId())) {
                LOGGER.warn("Ignoring fragment request from unknown network");
                return null;
            }
            String path = propertiesContainer.getStorageProperties().getFragmentPath();
            return new FragmentPacket(SecurityController.getInstance().loadFragment(path));

        } else {
            LOGGER.warn("Unknown request {}", request.getClass());
            return null;
        }
    }

    @Override
    public void error(String error) {
        LOGGER.error("Network error: {}", error);
        throw new RuntimeException("Network controller received a critical error. Please check the application log");
    }

    private Deque<ClientConnection> sendRequestToNetwork(Packet request, boolean requreResponse) {
        Deque<ClientConnection> runningConnections = new ArrayDeque<>();

        // Let the listener run its discovery flow (async); as it does it will feed prepared connections into its queue
        // which we pull from dynamically during the flow, in order to start the connection's get-fragment flow
        discoveryListener.startNetworkDiscovery();
        while (discoveryListener.isDiscovering()) {
            ClientConnection connection = discoveryListener.getNextNodeConnection();
            if (connection != null) {
                connection.setRequest(request, requreResponse);
                connection.start();

                LOGGER.debug("Offering node {} to queue", connection);
                runningConnections.offer(connection);
            } else {
                try {
                    Thread.sleep(IDLE_MS);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        LOGGER.debug("Finished sending request to network");
        return runningConnections;
    }

    private Deque<ClientConnection> sendRequestsToNetwork(Deque<Packet> requests, boolean requireResponse) {
        Deque<ClientConnection> runningConnections = new ArrayDeque<>();

        // Let the listener run its discovery flow (async); as it does it will feed prepared connections into its queue
        // which we pull from dynamically during the flow, in order to start the connection's get-fragment flow
        discoveryListener.startNetworkDiscovery();
        while (discoveryListener.isDiscovering()) {
            ClientConnection connection = discoveryListener.getNextNodeConnection();
            if (connection != null) {
                connection.setRequest(requests.poll(), requireResponse);
                connection.start();

                LOGGER.debug("Offering node {} to queue", connection);
                runningConnections.offer(connection);
            } else {
                try {
                    Thread.sleep(IDLE_MS);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        LOGGER.debug("Finished sending requests to network");
        return runningConnections;
    }

    private ClientConnection getFinishedConnection(Deque<ClientConnection> connections) {
        ClientConnection peek = connections.peek();
        while (!Objects.requireNonNull(peek).isFinished()) {
            try {
                Thread.sleep(IDLE_MS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        return connections.poll();
    }
}
