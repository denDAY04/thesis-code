package edu.dk.asj.dpm.security;

import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @DisplayName("Initiate SAE session")
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

    @Test
    @DisplayName("Generate key with KDF")
    void deriveSecretKey() {
        byte[] baseKey = new byte[]{0x00, 0x18};
        SecretKey key = SecurityController.getInstance().deriveSecretKey(baseKey);
        assertNotNull(key, "The derived key is null");
        assertFalse(Arrays.equals(baseKey, key.getEncoded()), "Derived key must not be the same as the base key");
    }

    @Test
    @DisplayName("Encryption/Decryption")
    void encryptDecrypt() throws Exception {
        byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        byte[] baseKey = "123".getBytes(StandardCharsets.UTF_8);

        byte[] cipherText = SecurityController.getInstance().encrypt(data, baseKey);
        assertNotNull(cipherText, "Cipher-text is null");
        assertFalse(Arrays.equals(data, cipherText), "Original data and cipher-text is the same");

        byte[] clearText = SecurityController.getInstance().decrypt(cipherText, baseKey);
        assertNotNull(clearText, "Clear-text is null");
        assertArrayEquals(data, clearText, "Original data and clear-text is not the same");
    }

    @Test
    @DisplayName("Encryption/Decryption fails for different base key")
    void encryptDecryptFail() throws Exception {
        byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        byte[] baseKey = "123".getBytes(StandardCharsets.UTF_8);
        byte[] otherKey = "1234".getBytes(StandardCharsets.UTF_8);

        byte[] cipherText = SecurityController.getInstance().encrypt(data, baseKey);
        assertThrows(AEADBadTagException.class,
                () -> SecurityController.getInstance().decrypt(cipherText, otherKey),
                "Decryption with bad key did not trigger failed MAC check");
    }


    @Test
    @DisplayName("ECC is thread safe")
    void threadSafeECC() throws InterruptedException {
        SecurityController.getInstance().setMasterPassword("123");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        final SAESession[] sessions = new SAESession[3];

        Thread t1 = new Thread(() -> {
           sessions[0] = SecurityController.getInstance().initiateSaeSession(id1, id2);
        });
        Thread t2 = new Thread(() -> {
            sessions[1] = SecurityController.getInstance().initiateSaeSession(id1, id2);
        });
        Thread t3 = new Thread(() -> {
            sessions[2] = SecurityController.getInstance().initiateSaeSession(id1, id2);
        });

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        assertNotNull(sessions[0], "Session 0 is null");
        assertNotNull(sessions[1], "Session 1 is null");
        assertNotNull(sessions[2], "Session 2 is null");
    }
}