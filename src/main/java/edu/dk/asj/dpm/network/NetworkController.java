package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.FragmentPacket;
import edu.dk.asj.dpm.network.packets.GetFragmentPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Stack;

public class NetworkController implements DiscoveryHandler, PacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkController.class);

    private List<ServerConnection> serverConnections;
    private DiscoveryListener discoveryListener;
    private final BigInteger networkId;

    public NetworkController(NetworkProperties properties) {
        serverConnections = new ArrayList<>();
        networkId = properties.getNetworkId();
    }

    public void startDiscoveryListener() {
        discoveryListener = DiscoveryListener.start(this, networkId);
    }

    public VaultFragment[] getNetworkFragments() throws IOException {
        Deque<ClientConnection> connections = discoveryListener.getNetworkConnections();
        if (connections.isEmpty()) {
            return null;
        }

        connections.forEach(connection -> {
            connection.setRequest(new GetFragmentPacket(networkId));
            connection.start();
        });



        /*
            while deque is not empty
                peek deque
                if conn ready
                    pop deque
                    get error or vault fragment
                    if error
                        throw error
                    endif
                    add fragment
                endif
            endwhile
         */

    }

    @Override
    public Packet process(Packet packet, SocketAddress sender) {
        if (packet instanceof DiscoveryPacket) {
            ServerConnection serverConnection = ServerConnection.open(this);
            serverConnections.add(serverConnection);
            return new DiscoveryEchoPacket(serverConnection.getPort());
        } else if (packet instanceof DiscoveryEchoPacket) {
            FragmentPacket fragmentPacket = new FragmentPacket();
            ClientConnection clientConnection = ClientConnection.send(fragmentPacket, sender);
        }

    }

    @Override
    public void error(String error) {

    }

    @Override
    public Packet process(Packet request, Closeable connection) {
        return null;
    }

    @Override
    public void error(String error, Closeable connection) {

    }
}
