package edu.dk.asj.dpm.network.packets;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Packet for discovering nodes in the node network for a given network ID.
 */
public class DiscoveryPacket extends Packet {
    private static final long serialVersionUID = 8787602385844921665L;

    private final BigInteger networkId;

    /**
     * Construct a discovery packet for the given network.
     * @param networkId the ID of the network that should reply to this packet.
     */
    public DiscoveryPacket(BigInteger networkId) {
        this.networkId = networkId;
    }

    /**
     * Get the network ID.
     * @return the network ID.
     */
    public BigInteger getNetworkId() {
        return networkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveryPacket)) return false;
        DiscoveryPacket that = (DiscoveryPacket) o;
        return networkId.equals(that.networkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId);
    }

    @Override
    public String toString() {
        return DiscoveryPacket.class + "{networkId:" + networkId + "}";
    }
}
