package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;

import java.net.SocketAddress;

/**
 * Interface for handling network discovery requests.
 */
public interface DiscoveryHandler {
    /**
     * Process a network discovery request.
     * @param packet the discovery request.
     * @param sender the address of the node sending the request.
     * @return the echo response to the discovery request.
     */
    DiscoveryEchoPacket process(DiscoveryPacket packet, SocketAddress sender);

    /**
     * Handle an error.
     * @param error the error
     */
    void error(String error);
}
