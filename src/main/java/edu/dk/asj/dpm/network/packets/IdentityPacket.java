package edu.dk.asj.dpm.network.packets;

import java.util.Objects;
import java.util.UUID;

public class IdentityPacket extends Packet {
    private static final long serialVersionUID = -8897636234832664126L;

    private final UUID nodeId;

    /**
     * Construct an identity packet with the node ID
     * @param nodeId the node's ID.
     */
    public IdentityPacket(UUID nodeId) {
        Objects.requireNonNull(nodeId, "Node ID must not be null");
        this.nodeId = nodeId;
    }

    /**
     * Get the node ID.
     * @return the ID.
     */
    public UUID getNodeId() {
        return nodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityPacket)) return false;
        IdentityPacket that = (IdentityPacket) o;
        return nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}