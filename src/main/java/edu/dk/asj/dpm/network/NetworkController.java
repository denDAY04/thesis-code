package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.FragmentPacket;
import edu.dk.asj.dpm.network.packets.GetFragmentPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class NetworkController implements DiscoveryHandler, PacketHandler, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkController.class);
    private static final long WAIT_CONNECTION_MS = 10;

    private DiscoveryListener discoveryListener;
    private final PropertiesContainer propertiesContainer;
    private final BigInteger networkId;
    private int networkSize;

    public NetworkController(NetworkProperties properties, PropertiesContainer propertiesContainer) {
        networkId = properties.getNetworkId();
        this.propertiesContainer = propertiesContainer;
        discoveryListener = DiscoveryListener.open(this, networkId);
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
        networkSize = 1;
        Deque<ClientConnection> connections = discoveryListener.getNetworkConnections();
        if (connections.isEmpty()) {
            return Collections.emptyList();
        }

        connections.forEach(connection -> {
            connection.setRequest(new GetFragmentPacket(networkId), true);
            connection.start();
        });

        List<VaultFragment> fragments = new ArrayList<>(connections.size());
        boolean hasError = false;
        while (!connections.isEmpty()) {
            ClientConnection peek = connections.peek();
            if (peek.isFinished()) {
                ClientConnection connection = connections.poll();
                if (connection.getResponse() != null) {
                    if (connection.getResponse() instanceof FragmentPacket) {
                        networkSize++;
                        fragments.add(((FragmentPacket) connection.getResponse()).getFragment());
                    } else {
                        LOGGER.warn("Unexpected reply to network fragment request. Expected {} but was {}", FragmentPacket.class, connection.getClass());
                        hasError = true;
                    }

                } else if (connection.getError() != null) {
                    LOGGER.warn("Fragment request error: {}", peek.getError());
                    hasError = true;
                } else {
                    LOGGER.debug("Connection {} finished with no response or error", connection.getName());
                }
            } else {
                try {
                    Thread.sleep(WAIT_CONNECTION_MS);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            if (hasError) {
                connections.forEach(ClientConnection::close);
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

        Deque<ClientConnection> connections = discoveryListener.getNetworkConnections();
        if (connections.isEmpty()) {
            LOGGER.warn("No network connections to send new fragments to");
            return false;
        }
        if (connections.size() != fragments.length) {
            LOGGER.warn("Cannot send {} fragments to {} nodes", fragments.length, connections.size());
            return false;
        }

        int i = 0;
        for (ClientConnection connection : connections) {
            connection.setRequest(new FragmentPacket(fragments[i++]), false);
            connection.start();
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

    private ClientConnection getFinishedConnection(Deque<ClientConnection> connections) {
        if (connections.isEmpty()) {
            return null;
        }

        ClientConnection peek = connections.peek();
        while (!peek.isFinished()) {
            try {
                Thread.sleep(WAIT_CONNECTION_MS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        return connections.poll();
    }

    @Override
    public void close() {
        LOGGER.info("Closing network resources");
        discoveryListener.close();
    }

    @Override
    public DiscoveryEchoPacket process(DiscoveryPacket packet) {
        ServerConnection connection = ServerConnection.open(this);
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
}
