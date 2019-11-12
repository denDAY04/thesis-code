package edu.dk.asj.dpm.security;

import edu.dk.asj.dpm.util.StorageHelper;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Singleton controlling the application's different security schemes.
 */
public class SecurityController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityController.class);
    private static final String RANDOM_GENERATOR_SCHEME = "DRBG";
    private static final String HASH_SCHEME = "SHA3-512";

    private static SecurityController instance;

    private byte[] mpHash;


    private SecurityController() {
        Security.addProvider(new BouncyCastleProvider());

        try {
            SecureRandom.getInstance(RANDOM_GENERATOR_SCHEME);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid random generator algorithm ["+e.getLocalizedMessage()+"]");
        }

        try {
            MessageDigest.getInstance(HASH_SCHEME, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid hash algorithm ["+e.getLocalizedMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid hash algorithm provider ["+e.getLocalizedMessage()+"]");
        }
    }

    /**
     * Get the singleton instance.
     * @return the security scheme instance.
     */
    public static SecurityController getInstance() {
        if (instance == null) {
            instance = new SecurityController();
        }
        return instance;
    }

    /**
     * Retrieve a new instance of the scheme's secure random number generator.
     * @return a secure random instance.
     */
    public SecureRandom getRandomGenerator() {
        try {
            return SecureRandom.getInstance(RANDOM_GENERATOR_SCHEME);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid random generator algorithm ["+e.getLocalizedMessage()+"]");
        }
    }

    /**
     * Retrieve a new instance of the scheme's hash function.
     * @return a {@link MessageDigest} object encapsulating the hash function.
     */
    public MessageDigest getHashFunction() {
        try {
            return MessageDigest.getInstance(HASH_SCHEME, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid hash algorithm ["+e.getLocalizedMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid hash algorithm provider ["+e.getLocalizedMessage()+"]");
        }
    }

    /**
     * Check if a user's input master password is equal to the stored MP hash.
     * @param inputMp the user's master password input. It must not have been processed with any hashing prior to use
     *                in this method.
     * @return true if the input is equal to the stored master password verification hash, after processing the user
     * input.
     */
    public boolean isMasterPassword(String inputMp) {
        if (mpHash == null || inputMp == null || inputMp.isBlank()) {
            return false;
        }
        byte[] inputHash = hash(inputMp);
        return Arrays.equals(mpHash, inputHash);
    }

    /**
     * Store a verification hash of the user's master password in-memory.
     * @param inputMp the user's input for their master password. It must not have been processed with any hashing prior
     *                to use in this method.
     */
    public void setMasterPassword(String inputMp) {
        this.mpHash = hash(inputMp);
    }

    /**
     * Clear the in-memory stored master password verification hash. This method wipes the storage space of the hash
     * and then discards the handle.
     */
    public void clearMasterPassword() {
        if (mpHash != null) {
            Arrays.fill(mpHash, (byte) 0x00);
            mpHash = null;
        }
    }

    /**
     * Load the local fragment from the device.
     * @param storagePath the path on where to find the fragment.
     * @return the loaded fragment, or null.
     */
    public VaultFragment loadFragment(String storagePath) {
        StandardOpenOption[] fileOptions = new StandardOpenOption[] { READ };
        try (InputStream fileStream = Files.newInputStream(Paths.get(storagePath), fileOptions);
             ObjectInputStream objectStream = new ObjectInputStream(fileStream)) {

            // TODO decrypt fragment

            return (VaultFragment) objectStream.readObject();
        } catch (IOException e) {
            LOGGER.warn("Could not read fragment file", e);
            return null;
        } catch (ClassNotFoundException e) {
            LOGGER.error("Fragment file data was corrupted", e);
            return null;
        }
    }

    /**
     * Save a fragment to the node's device at the given storage path.
     * @param fragment the fragment to be saved.
     * @param storagePath the path to where the fragment will be saved.
     * @return true if the save succeeded, false otherwise.
     */
    public boolean saveFragment(VaultFragment fragment, String storagePath) {
        StandardOpenOption[] fileOptions = new StandardOpenOption[] { WRITE, TRUNCATE_EXISTING };
        try (OutputStream fileStream = Files.newOutputStream(StorageHelper.getOrCreateStoragePath(storagePath), fileOptions);
             ObjectOutputStream objectWriter = new ObjectOutputStream(fileStream)) {

            // TODO encrypt fragment

            objectWriter.writeObject(fragment);
            return true;
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not create fragment file", e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Could not write to fragment file", e);
            return false;
        }
    }

    private byte[] hash(String s) {
        Objects.requireNonNull(s);
        return getHashFunction().digest(s.getBytes(StandardCharsets.UTF_8));
    }
}
