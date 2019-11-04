package dk.asj.dpm.vault;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SecureVaultTest {

    private SecureVault.Builder vaultBuilder;

    @BeforeEach
    void initEach() {
        vaultBuilder = SecureVault.builder();
    }

    @Test
    @DisplayName("Add entry to vault")
    void add() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault vault = vaultBuilder.buildEmpty();
        boolean added = vault.add(new VaultEntry("foo", "bar"));
        assertTrue(added, "Add entry failed");
    }

    @Test
    @DisplayName("Search vault")
    void search() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault vault = vaultBuilder.buildEmpty();
        vault.add(new VaultEntry("foo", "bar"));
        vault.add(new VaultEntry("fooBar", "baz"));
        vault.add(new VaultEntry("Alice", "Bob"));

        Set<VaultEntry> result = vault.search("Foo");
        assertEquals(2, result.size(), "Unexpected search result size");

        Iterator<VaultEntry> resultIter = result.iterator();
        assertEquals("foo", resultIter.next().getName(), "Non-matching entry names");
        assertEquals("fooBar", resultIter.next().getName(), "Non-matching entry names");
    }

    @Test
    @DisplayName("Get all vault entries")
    void getAll() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault vault = vaultBuilder.buildEmpty();
        vault.add(new VaultEntry("foo", "bar"));
        vault.add(new VaultEntry("fooBar", "baz"));

        Set<VaultEntry> entries = vault.getAll();
        assertEquals(2, entries.size(), "Unexpected result size");
    }

    @Test
    @DisplayName("Remove entry from vault")
    void delete() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault vault = vaultBuilder.buildEmpty();
        vault.add(new VaultEntry("foo", "bar"));

        boolean wasRemoved = vault.remove(new VaultEntry("foo", "bar"));
        assertTrue(wasRemoved, "Entry was not removed");
    }

    @Test
    @DisplayName("Fragment vault")
    void fragment() throws IOException {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault vault = vaultBuilder.buildEmpty();
        int fragmentCount = 2;
        VaultFragment[] fragments = vault.fragment(fragmentCount);
        assertEquals(fragmentCount, fragments.length, "Unexpected fragment count");
        assertNotNull(fragments[0], "Fragment 0 is null");
        assertNotNull(fragments[1], "Fragment 1 is null");
    }

    @Test
    @DisplayName("Get builder")
    void builder() {
        assertNotNull(vaultBuilder, "Builder is null");
    }

    @Test
    @DisplayName("Build empty vault")
    void buildEmpty() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        assertNotNull(vaultBuilder.buildEmpty(), "Empty vault is null");
    }

    @Test
    @DisplayName("Build vault fails when incomplete")
    void buildFromFragmentsFail() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        // Without initial fragment
        assertThrows(IllegalStateException.class, () -> vaultBuilder.build(), "Fresh builder does not throw exception");

        // With initial fragment
        VaultFragment fragment = new VaultFragment(new int[]{0}, new byte[]{0x1f}, 2);
        vaultBuilder.addFragment(fragment);
        assertThrows(IllegalStateException.class, () -> vaultBuilder.build(), "Incomplete builder does not throw exception");
    }

    @Test
    @DisplayName("Build vault from fragments")
    void buildFromFragments() throws IOException, ClassNotFoundException {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        SecureVault dummyVault = vaultBuilder.buildEmpty();
        byte[] bytes;
        int[] mask;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos))  {
            oos.writeObject(dummyVault);
            bytes = bos.toByteArray();
            mask = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                mask[i] = i;
            }
        } catch (Exception ex) {
            fail(ex);
            return;
        }

        VaultFragment fragment = new VaultFragment(mask, bytes, bytes.length);
        vaultBuilder.addFragment(fragment);

        SecureVault vault = assertDoesNotThrow(() -> vaultBuilder.build(), "Valid vault build throws exception");
        assertNotNull(vault, "Vault is null");
    }

    @Test
    @DisplayName("Add valid fragment to builder")
    void addFragment() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        VaultFragment f1 = new VaultFragment(new int[]{0}, new byte[]{0x1f}, 2);
        assertDoesNotThrow(() ->vaultBuilder.addFragment(f1), "Adding valid fragment threw exception");
    }

    @Test
    @DisplayName("Add invalid fragment to builder fails")
    void addInvalidFragment() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        VaultFragment f1 = new VaultFragment(new int[]{0}, new byte[]{0x1f}, 2);
        vaultBuilder.addFragment(f1);
        VaultFragment f2 = new VaultFragment(new int[]{1}, new byte[]{0x1f}, 1);
        assertThrows(IllegalArgumentException.class, () -> vaultBuilder.addFragment(f2), "Adding invalid fragment does not throw exception");
    }

    @Test
    @DisplayName("Add fragments complete the builder")
    void completeBuilder() {
        assumeTrue(vaultBuilder != null, "Vault builder is null");

        assertFalse(vaultBuilder.isComplete(), "Fresh builder is already complete");
        VaultFragment f1 = new VaultFragment(new int[]{0}, new byte[]{0x1f}, 2);
        vaultBuilder.addFragment(f1);
        VaultFragment f2 = new VaultFragment(new int[]{1}, new byte[]{0x1f}, 2);
        vaultBuilder.addFragment(f2);
        assertTrue(vaultBuilder.isComplete(), "Filled builder is not complete");
    }
}