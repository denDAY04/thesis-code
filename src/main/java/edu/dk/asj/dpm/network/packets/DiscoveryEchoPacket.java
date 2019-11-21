package edu.dk.asj.dpm.network.packets;

import java.util.Objects;

/**
 * Packet for echoing a response to a {@link DiscoveryPacket}. This response includes the port number on which the
 * sender of the discovery request should initiate a session connection.
 */
public class DiscoveryEchoPacket extends Packet {
    private static final long serialVersionUID = 8379654888314823172L;

    private final int connectionPort;

    /**
     * Construct the request with a connection port for the receiver of this request to connect to.
     * @param connectionPort the connection port
     */
    public DiscoveryEchoPacket(int connectionPort) {
        this.connectionPort = connectionPort;
    }

    /**
     * Get the connection port.
     * @return the port.
     */
    public int getConnectionPort() {
        return connectionPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveryEchoPacket)) return false;
        DiscoveryEchoPacket that = (DiscoveryEchoPacket) o;
        return connectionPort == that.connectionPort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionPort);
    }

    @Override
    public String toString() {
        return DiscoveryEchoPacket.class + "{connectionPort:" + connectionPort + "}";
    }
}
