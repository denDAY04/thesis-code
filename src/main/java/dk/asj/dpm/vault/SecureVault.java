package dk.asj.dpm.vault;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class SecureVault {

    private Set<VaultEntry> entries;

    private SecureVault() {
        entries = new TreeSet<>(Comparator.comparing(VaultEntry::getName));
    }

    public Set<VaultEntry> search(String nameQuery) {
        return entries.parallelStream().filter(e -> e.getName().contains(nameQuery)).collect(Collectors.toSet());
    }

    public Set<VaultEntry> getAll() {
        return entries;
    }

    public boolean add(VaultEntry entry) {
        return entries.add(entry);
    }

    public boolean delete(VaultEntry entry) {
        return entries.remove(entry);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ByteBuffer buffer;
        private int byteCounter;
        private int finalVaultSize;

        public Builder() {
            byteCounter = 0;
            // buffer isn't initialised until the first fragment request, since the final size is not known until then
        }

        public void addFragment(VaultFragment fragment) throws IllegalStateException {
            if (isComplete()) {
                throw new IllegalStateException("Fragment list complete");
            }

            if (buffer == null) {
                finalVaultSize = fragment.getTotalVaultByteSize();
                buffer = ByteBuffer.allocate(finalVaultSize);
            }

            if (finalVaultSize != fragment.getTotalVaultByteSize()) {
                throw new IllegalArgumentException("Fragment reports unexpected total vault byte-size");
            }

            for (int i = 0; i < fragment.getMask().length; ++i, ++byteCounter) {
                int maskIndex = fragment.getMask()[i];
                byte dataByte = fragment.getFragment()[i];
                buffer.put(maskIndex, dataByte);
            }
        }

        public boolean isComplete() {
            return byteCounter == finalVaultSize;
        }

        public SecureVault build() throws IOException, ClassNotFoundException, IllegalStateException {
            if (!isComplete()) {
                throw new IllegalStateException("Vault fragments are not complete");
            }

            buffer.flip();
            byte[] backingArray;
            if (buffer.hasArray()) {
                backingArray = buffer.array();
            } else {
                backingArray = new byte[finalVaultSize];
                buffer.get(backingArray);
            }

            ObjectInputStream objWriter = new ObjectInputStream(new ByteArrayInputStream(backingArray));
            return (SecureVault) objWriter.readObject();
        }
    }
}
