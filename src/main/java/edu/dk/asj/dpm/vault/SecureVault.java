package edu.dk.asj.dpm.vault;

import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.util.BufferHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * This class encapsulates the facilities of a secure vault storing vault entries, including addition, removal, and
 * searching of vault entries. This class can be initiated as an empty vault, or constructed from vault fragments.
 * Equally it can de-fragment the vault into fragments.<p>
 * <p>
 * All handling of raw byte data is done in unencrypted contexts, thus the user of this class should take care that any
 * data sent to the vault (as entries or fragments) are done so in an unencrypted state.
 */
public class SecureVault implements Serializable {
    private static final long serialVersionUID = 2528173234528631366L;

    private final Set<VaultEntry> entries;

    private SecureVault() {
        entries = new TreeSet<>(new VaultEntryNameComparator());
    }

    /**
     * Search the vault for entries matching a given name query. This search is case-insensitive and matches on both
     * complete strings and sub-strings.
     * @param nameQuery query to filter on. Results include entries whose name contains the complete character sequence,
     *                  either in full or as a sub-string of the entry's name.
     * @return the result as an index-enabled list. May be empty.
     */
    public List<VaultEntry> search(String nameQuery) {
        Set<VaultEntry> resultSet = entries.stream()
                .filter(e -> e.getName().toLowerCase().contains(nameQuery.toLowerCase()))
                .collect(Collectors.toCollection(() -> new TreeSet<>(new VaultEntryNameComparator())));
        return asList(resultSet);
    }

    /**
     * Get all entries in the vault, as an index-enabled list.
     * @return all the entries.
     */
    public List<VaultEntry> getAll() {
        return asList(entries);
    }

    /**
     * Add an entry to the vault.
     * @param entry entry to be added in the vault.
     * @return true if the entry was added, false if the vault already contains the entry.
     */
    public boolean add(VaultEntry entry) {
        return entries.add(entry);
    }

    /**
     * Removes an entry from the vault.
     * @param entry the entry to be removed.
     * @return true if the entry was removed, false if it was never in the vault.
     */
    public boolean remove(VaultEntry entry) {
        return entries.remove(entry);
    }

    /**
     * Fragment the vault.
     * @param count number of fragments to split the vault into.
     * @return vault fragments.
     * @throws IOException if an IO error occurs during vault serialization.
     */
    public VaultFragment[] fragment(int count) throws IOException {
        if (count < 1) {
            throw new IllegalArgumentException("Fragment count must be > 0");
        }

        byte[] data;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos))  {
            oos.writeObject(this);
            data = bos.toByteArray();
        }

        VaultFragment.Builder[] builders = new VaultFragment.Builder[count];
        for (int i = 0; i < builders.length; i++) {
            builders[i] = VaultFragment.builder(data.length);
        }

        SecureRandom randomGenerator = SecurityController.getInstance().getRandomGenerator();
        for (int i = 0; i < data.length; i++) {
            int selector = randomGenerator.nextInt(count);
            builders[selector].addByte(i, data[i]);
        }

        VaultFragment[] fragments = new VaultFragment[builders.length];
        for (int i = 0; i < builders.length; i++) {
            fragments[i] = builders[i].build();
        }

        return fragments;
    }

    /**
     * Get builder for constructing a secure vault.
     * @return vault builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    private ArrayList<VaultEntry> asList(Set<VaultEntry> entries) {
        ArrayList<VaultEntry> list = new ArrayList<>();
        if (entries != null) {
            list.addAll(entries);
        }
        return list;
    }


    /**
     * Builder class for constructing a secure vault. This can either build an empty vault, or iteratively process
     * {@link VaultFragment}s to build an existing vault.
     */
    public static class Builder {
        private ByteBuffer buffer;
        private int byteCounter;
        private int finalVaultSize;

        public Builder() {
            byteCounter = 0;
            finalVaultSize = -1;
            // buffer isn't initialised until the first fragment request, since the final size is not known until then
        }

        /**
         * Add a vault fragment to the builder to be used for building a complete vault.
         * @param fragment the vault fragment.
         * @throws IllegalStateException if the builder is complete and is waiting to build the vault object.
         * @throws IllegalArgumentException if a fragment reports a different vault size than the fragment initially
         * added to the builder.
         * @return this builder.
         */
        public Builder addFragment(VaultFragment fragment) throws IllegalStateException, IllegalArgumentException {
            if (isComplete()) {
                throw new IllegalStateException("Fragment list complete");
            }

            if (fragment != null) {
                if (buffer == null) {
                    finalVaultSize = fragment.getVaultSize();
                    buffer = ByteBuffer.allocate(finalVaultSize);
                }

                if (finalVaultSize != fragment.getVaultSize()) {
                    throw new IllegalArgumentException("Fragment reports unexpected total vault byte-size");
                }

                for (int i = 0; i < fragment.getMask().length; ++i, ++byteCounter) {
                    int maskIndex = fragment.getMask()[i];
                    byte dataByte = fragment.getFragment()[i];
                    buffer.put(maskIndex, dataByte);
                }
            }

            return this;
        }

        /**
         * Check whether the builder has received all the required fragments to build a vault. Use this method prior to
         * calling {@link Builder#build()} to ensure the builder is ready.
         * @return true if the builder has received all required fragments to build a vault object, false otherwise.
         */
        boolean isComplete() {
            return byteCounter == finalVaultSize;
        }

        /**
         * Build an initiated vault from existing fragments. This requires previous calls to {@link Builder#addFragment}
         * in order to load the builder with the vault's fragments.<p>
         * <p>
         * A call to {@link Builder#isComplete} should be made prior to calling this method, in order  to check whether
         * the builder has been fed all the required fragments to build the vault.
         * @return The initiated vault.
         * @throws ClassNotFoundException If the fragments could not be deserialized into a {@link SecureVault} object.
         * @throws IllegalStateException if the vault is not complete, i.e. not all fragments have been added.
         * @throws IOException if a buffer IO error occurs
         */
        public SecureVault build() throws IOException, ClassNotFoundException, IllegalStateException {
            if (!isComplete()) {
                throw new IllegalStateException("Vault fragments are not complete");
            }

            byte[] data = BufferHelper.readAndClear(buffer);
            ObjectInputStream objWriter = new ObjectInputStream(new ByteArrayInputStream(data));
            return (SecureVault) objWriter.readObject();
        }

        /**
         * Build an empty vault. This does not require any previous calls to add fragments, and any such fragments will
         * be ignored
         * @return An empty vault.
         */
        public SecureVault buildEmpty() {
            return new SecureVault();
        }
    }


    /**
     * Serializable comparator for vault entries, ordering by entry names.
     */
    private static class VaultEntryNameComparator implements Comparator<VaultEntry>, Serializable {
        private static final long serialVersionUID = -3779791269190442222L;

        @Override
        public int compare(VaultEntry e1, VaultEntry e2) {
            return e1.getName().compareTo(e2.getName());
        }
    }
}
