package dk.asj.dpm.vault;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class VaultFragment implements Serializable {
    private static final long serialVersionUID = -4962470314049701456L;

    private final byte[] fragment;
    private final int[] mask;
    private final int vaultSize;

    public VaultFragment(int[] mask, byte[] fragment, int vaultSize) {
        this.mask = mask;
        this.fragment = fragment;
        this.vaultSize = vaultSize;
    }

    public byte[] getFragment() {
        return fragment;
    }

    public int[] getMask() {
        return mask;
    }

    public int getVaultSize() {
        return vaultSize;
    }

    /**
     * Get a builder for constructing a vault fragment through iterative addition of fragment data.
     * @param vaultSize the total byte-size of the vault this fragment will (partly) represent.
     * @return the builder.
     */
    public static Builder builder(int vaultSize) {
        return new Builder(vaultSize);
    }



    /**
     * Builder for constructing a vault fragment through iteratively adding byte data.
     */
    public static class Builder {
        private int vaultSize;
        private ByteBuffer byteBuffer;
        private IntBuffer maskBuffer;

        /**
         * Initialise the builder with the given vault size. This defines the size of the mask and byte-data buffers,
         * as well as being used in the generated vault fragment when {@link Builder#build()} is called.
         * @param vaultSize total byte-size of the complete vault this fragment will be created from.
         */
        private Builder(int vaultSize) {
            if (vaultSize < 1) {
                throw new IllegalArgumentException("Vault size must be > 0");
            }
            this.vaultSize = vaultSize;
            byteBuffer = ByteBuffer.allocate(vaultSize);
            maskBuffer = IntBuffer.allocate(vaultSize);

        }

        /**
         * Add a byte of data with the given mask-index to the builder.
         * @param maskIndex 0-based index of the byte's location in the complete vault's serialized byte array.
         * @param data the byte data.
         * @return this builder.
         */
        public Builder addByte(int maskIndex, byte data) {
            if (maskIndex < 0) {
                throw new IllegalArgumentException("Mask index for byte must be >= 0");
            }
            maskBuffer.put(maskIndex);
            byteBuffer.put(data);
            return this;
        }

        /**
         * Build the vault fragment.
         * @return the vault fragment.
         */
        public VaultFragment build() {
            byteBuffer.flip();
            byte[] byteArray = new byte[byteBuffer.limit()];
            byteBuffer.get(byteArray, 0, byteBuffer.limit());

            maskBuffer.flip();
            int [] maskArray = new int[maskBuffer.limit()];
            maskBuffer.get(maskArray, 0, maskBuffer.limit());

            return new VaultFragment(maskArray, byteArray, vaultSize);
        }
    }
}
