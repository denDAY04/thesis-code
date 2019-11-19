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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityControllerTest {

    private static String PASSWORD = "123";

    @Test
    @Order(1)
    @DisplayName("Save fragment")
    void saveFragment() throws IOException {
        VaultFragment fragment = new VaultFragment(new int[]{0}, new byte[]{0x00}, 1);
        String fragmentPath = PropertiesContainer.loadProperties().getStorageProperties().getFragmentPath();
        SecurityController.getInstance().setMasterPassword(PASSWORD);

        boolean saved = SecurityController.getInstance().saveFragment(fragment, fragmentPath);
        assertTrue(saved, "Fragment was not saved");

        boolean fileExists = Files.exists(Paths.get(fragmentPath));
        assertTrue(fileExists, "File was not found on the system");
    }

    @Test
    @Order(2)
    @DisplayName("Load fragment")
    void loadFragment() throws IOException {
        String fragmentPath = PropertiesContainer.loadProperties().getStorageProperties().getFragmentPath();
        SecurityController.getInstance().setMasterPassword(PASSWORD);

        VaultFragment fragment = SecurityController.getInstance().loadFragment(fragmentPath);
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

    @Test
    @DisplayName("Generate SAE parameters")
    void generateSAEParams() {
        SecurityController controller = SecurityController.getInstance();
        String password = "12345";
        controller.setMasterPassword(password);

        UUID identity1 = UUID.fromString("78870074-6f99-449e-981b-eb5a3f67b5f1");
        UUID identity2 = UUID.fromString("4c25c750-3413-49ca-8712-f441a7eb7e1d");
        SAESession session = controller.initiateSaeSession(identity1, identity2);

        assertNotNull(session, "SAE session is null");
        assertAll("Session data",
                () -> assertNotNull(session.getPwe(), "Password element is null"),
                () -> assertNotNull(session.getRand(), "Random is null"),
                () -> assertNotNull(session.getParameters().getScalar(), "Scalar parameters is null"),
                () -> assertNotNull(session.getParameters().getElem(), "Elem parameter is null"));
    }
}