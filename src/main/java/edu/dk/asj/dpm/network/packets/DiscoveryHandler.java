package edu.dk.asj.dpm.network.packets;

/**
 * Interface for handling network discovery requests.
 */
public interface DiscoveryHandler {
    /**
     * Process a network discovery request.
     * @param packet the discovery request.
     * @return the echo response to the discovery request.
     */
    DiscoveryEchoPacket process(DiscoveryPacket packet);

    /**
     * Handle an error.
     * @param error the error
     */
    void error(String error);
}
