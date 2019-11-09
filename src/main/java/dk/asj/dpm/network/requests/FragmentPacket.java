package dk.asj.dpm.network.requests;

import dk.asj.dpm.vault.VaultFragment;
import java.io.Serializable;
import java.util.Objects;

/**
 * Request containing a vault fragment.
 */
public class FragmentPacket extends Packet implements Serializable {
    private static final long serialVersionUID = -5156371335990354618L;

    private VaultFragment fragment;

    /**
     * Construct the request using a vault fragment.
     * @param fragment the vault fragment to be sent with this request.
     */
    public FragmentPacket(VaultFragment fragment) {
        this.fragment = fragment;
    }

    /**
     * Get the vault fragment.
     * @return the vault fragment.
     */
    public VaultFragment getFragment() {
        return fragment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FragmentPacket)) return false;
        FragmentPacket that = (FragmentPacket) o;
        return fragment.equals(that.fragment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fragment);
    }
}
