package edu.dk.asj.dpm.security;

import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityControllerTest {

    @Test
    @Order(1)
    @DisplayName("Save fragment")
    void saveFragment() throws IOException {
        VaultFragment fragment = new VaultFragment(new int[]{0}, new byte[]{0x00}, 1);
        String storagePath = "test-data/frag.data";

        boolean saved = SecurityController.getInstance().saveFragment(fragment, storagePath);
        assertTrue(saved, "Fragment was not saved");

        String fragmentPath = PropertiesContainer.loadProperties().getStorageProperties().getFragmentPath();
        boolean fileExists = Files.exists(Paths.get(fragmentPath));
        assertTrue(fileExists, "File was not found on the system");
    }

    @Test
    @Order(2)
    @DisplayName("Load fragment")
    void loadFragment() throws IOException {
        String storagePath = "test-data/frag.data";
        VaultFragment fragment = SecurityController.getInstance().loadFragment(storagePath);
        assertNotNull(fragment, "Fragment was not loaded");
    }

    @Test
    @DisplayName("Get singleton instance")
    void getInstance() {
        assertNotNull(SecurityController.getInstance(), "Instance is null");
    }

    @Test
    @DisplayName("Get random generator")
    void getRandomGenerator() {
        assertNotNull(SecurityController.getInstance().getRandomGenerator(), "Random generator is null");
    }

    @Test
    @DisplayName("Get hash function")
    void getHashFunction() {
        assertNotNull(SecurityController.getInstance().getHashFunction(), "Hash function is null");
    }

    @Test
    @DisplayName("Verify master password")
    void isMasterPassword() {
        SecurityController controller = SecurityController.getInstance();
        String password = "12345";
        controller.setMasterPassword(password);

        assertTrue(controller.isMasterPassword(password), "Passwords were not equal");
    }

    @Test
    @DisplayName("Clear master password")
    void clearMasterPassword() {
        SecurityController controller = SecurityController.getInstance();
        String password = "12345";
        controller.setMasterPassword(password);

        controller.clearMasterPassword();
        assertFalse(controller.isMasterPassword(password), "Password was not cleared");
    }
}