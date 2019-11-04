package dk.asj.dpm.vault;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * A vault fragment is a discrete object encapsulating a part of a (randomly) fragmented vault and consists of three
 * main properties:
 * <ul>
 *     <li><b>mask</b></li> is an array of indices defining the location of each byte of data from this fragment---
 *     where it was located in the complete vault's raw data array.
 *     <li><b>fragment</b> is an array of raw byte data from the vault</li>
 *     <li><b>vaultSize</b></li> denotes the total size of the complete vault's raw data array from which this fragment
 *     was created.
 * </ul>
 */
public class VaultFragment implements Serializable {
    private static final long serialVersionUID = -4962470314049701456L;

    private final byte[] fragment;
    private final int[] mask;
    private final int vaultSize;

    /**
     * Create a new vault fragment object.
     * @param mask int-array containing indices mapping this fragment's bytes into the original vault's complete data
     *             array. Must not be null.
     * @param fragment byte-array containing this fragment's raw data from a complete vault. Must not be null.
     * @param vaultSize the size of the complete vault's data array.
     */
    public VaultFragment(int[] mask, byte[] fragment, int vaultSize) {
        this.mask = Objects.requireNonNull(mask, "Mask must not be null");
        this.fragment = Objects.requireNonNull(fragment, "Mask must not be null");;

        if (vaultSize < 1) {
            throw new IllegalArgumentException("vaultSize must be > 0");
        }
        this.vaultSize = vaultSize;
    }

    /**
     * Get the raw byte data from this fragment.
     * @return the data.
     */
    public byte[] getFragment() {
        return fragment;
    }


    /**
     * Get the mask int-array mapping the byte-indices from this fragment into the byte array of the complete vault.
     * @return the mask.
     */
    public int[] getMask() {
        return mask;
    }

    /**
     * Get the original vault's total byte size.
     * @return the size.
     */
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
