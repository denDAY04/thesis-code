package edu.dk.asj.dpm.vault;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VaultFragmentTest {

    @Test
    @DisplayName("Get builder")
    void builder() {
        assertNotNull(VaultFragment.builder(1), "Builder is null");
    }


    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Get builder fails with invalid size")
    void builderInvalidSize(int vaultSize) {
        assertThrows(IllegalArgumentException.class, () -> VaultFragment.builder(vaultSize), "Builder does not throw proper exception");
    }

    @Test
    @DisplayName("Add fragment to builder")
    void addFragmentData() {
        VaultFragment.Builder builder = VaultFragment.builder(10);
        Assertions.assertDoesNotThrow(() -> builder.addByte(0, (byte) 0xff), "Unexpected exception");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    @DisplayName("Add fragment to builder fails for bad index")
    void addFragmentDataBadIndex(int maskIndex) {
        VaultFragment.Builder builder = VaultFragment.builder(1);
        assertThrows(IndexOutOfBoundsException.class, () -> builder.addByte(maskIndex, (byte) 0xff));
    }

    @Test
    @DisplayName("Build fragment")
    void buildFragment() {
        int vaultSize = 3;
        byte firstDataByte = (byte) 0xf0;
        int firstMaskIndex = 0;
        byte secondDataByte = (byte) 0xff;
        int secondMaskIndex = 1;

        VaultFragment.Builder builder = VaultFragment.builder(vaultSize)
                .addByte(firstMaskIndex, firstDataByte)
                .addByte(secondMaskIndex, secondDataByte);
        VaultFragment fragment = builder.build();

        assertEquals(vaultSize, fragment.getVaultSize(), "Unexpected fragment vault size");
        assertAll("Data",
                () -> assertEquals(2, fragment.getFragment().length, "Unexpected fragment data length"),
                () -> assertEquals(firstDataByte, fragment.getFragment()[0], "Unexpected first fragment byte"),
                () -> assertEquals(secondDataByte, fragment.getFragment()[1], "Unexpected second fragment byte")
        );
        assertAll("Mask",
                () -> assertEquals(2, fragment.getMask().length, "Unexpected fragment mask length"),
                () -> assertEquals(firstMaskIndex, fragment.getMask()[0], "Unexpected first mask index"),
                () -> assertEquals(secondMaskIndex, fragment.getMask()[1], "Unexpected second mask index")
        );
    }
}
