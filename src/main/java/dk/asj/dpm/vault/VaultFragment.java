package dk.asj.dpm.vault;

import java.io.Serializable;

public class VaultFragment implements Serializable {

    private static final long serialVersionUID = -4962470314049701456L;

    private final byte[] fragment;
    private final int[] mask;
    private final int totalVaultByteSize;

    public VaultFragment(int[] mask, byte[] fragment, int totalVaultSize) {
        this.mask = mask;
        this.fragment = fragment;
        this.totalVaultByteSize = totalVaultSize;
    }

    public byte[] getFragment() {
        return fragment;
    }

    public int[] getMask() {
        return mask;
    }

    public int getTotalVaultByteSize() {
        return totalVaultByteSize;
    }
}
