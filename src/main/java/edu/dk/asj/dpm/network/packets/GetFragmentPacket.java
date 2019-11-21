package edu.dk.asj.dpm.network.packets;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Packet for requesting a node's fragment.
 */
public class GetFragmentPacket extends Packet {
    private static final long serialVersionUID = -3818150600075892916L;

    private BigInteger networkId;

    /**
     * Construct a new packet.
     * @param networkId the ID of the network to send this packet to.
     */
    public GetFragmentPacket(BigInteger networkId) {
        this.networkId = networkId;
    }

    /**
     * Get the ID of the network for this packet.
     * @return the network ID.
     */
    public BigInteger getNetworkId() {
        return networkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GetFragmentPacket)) return false;
        GetFragmentPacket that = (GetFragmentPacket) o;
        return networkId.equals(that.networkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId);
    }

    @Override
    public String toString() {
        return GetFragmentPacket.class + "{networkId:"+networkId+"}";
    }
}
