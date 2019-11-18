package edu.dk.asj.dpm.security;

import edu.dk.asj.dpm.util.StorageHelper;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
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

    private static final String HASH_SCHEME_SHORT = "SHA3-256";

    private static final String CIPHER_SCHEME = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 96; // IV length recommended by NIST

    private static final String KDF_SCHEME = "PBKDF2WithHmacSHA3-256";
    private static final int KDF_ITERATIONS = 1000;
    private static final int KDF_LENGTH = 256;

    private static SecurityController instance;

    private byte[] mpDerivative;


    private SecurityController() {
        Security.addProvider(new BouncyCastleProvider());

        getRandomGenerator();
        getShortHashFunction();
        getCipherEngine();

        try {
            SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid KDF algorithm ["+e.getMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid KDF algorithm provider ["+e.getMessage()+"]");
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
     * Check if a user's input master password is equal to the stored MP hash.
     * @param pwd the user's master password input. It must not have been processed with any hashing prior to use
     *                in this method.
     * @return true if the input is equal to the stored master password verification hash, after processing the user
     * input.
     */
    public boolean isMasterPassword(String pwd) {
        Objects.requireNonNull(pwd);
        byte[] inputHash = generatePasswordDerivative(pwd);
        return Arrays.equals(mpDerivative, inputHash);
    }

    /**
     * Store a verification hash of the user's master password in-memory.
     * @param pwd the user's input for their master password. It must not have been processed with any hashing prior
     *                to use in this method.
     */
    public void setMasterPassword(String pwd) {
        Objects.requireNonNull(pwd);
        this.mpDerivative = generatePasswordDerivative(pwd);
    }

    /**
     * Compute the unique network ID using the user's master password and network ID seed.
     * @param password the user's master password (in plaintext).
     * @param seed a seed for the network ID generation.
     * @return the generated network ID.
     */
    public BigInteger computeNetworkId(String password, String seed) {
        Objects.requireNonNull(password, "Master password must not be null");
        Objects.requireNonNull(seed, "Network ID seed must not be null");

        MessageDigest hashFunction = getShortHashFunction();
        hashFunction.update(generatePasswordDerivative(password));
        hashFunction.update(seed.getBytes(StandardCharsets.UTF_8));
        return new BigInteger(hashFunction.digest());
    }

    /**
     * Clear the in-memory stored master password verification hash. This method wipes the storage space of the hash
     * and then discards the handle.
     */
    public void clearMasterPassword() {
        if (mpDerivative != null) {
            Arrays.fill(mpDerivative, (byte) 0x00);
            mpDerivative = null;
        }
    }

    /**
     * Load the local fragment from the device.
     * @param storagePath the path on where to find the fragment.
     * @return the loaded fragment, or null.
     */
    public VaultFragment loadFragment(String storagePath) {
        LOGGER.debug("Loading local fragment");

        StandardOpenOption[] fileOptions = new StandardOpenOption[] { READ };
        try (InputStream fileStream = Files.newInputStream(Paths.get(storagePath), fileOptions)){
            byte[] data = decryptFragment(fileStream.readAllBytes());
            VaultFragment fragment;
            try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
                 ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
                fragment = (VaultFragment) objectStream.readObject();
            }
            return fragment;
        } catch (Exception e) {
            LOGGER.warn("Could not read from fragment file", e);
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
        LOGGER.debug("Saving local fragment");

        StandardOpenOption[] fileOptions = new StandardOpenOption[] { WRITE, TRUNCATE_EXISTING };
        try (OutputStream fileStream = Files.newOutputStream(StorageHelper.getOrCreateStoragePath(storagePath), fileOptions);
             ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)){

            objectStream.writeObject(fragment);
            byte[] serializedFragment = byteStream.toByteArray();
            fileStream.write(encryptFragment(serializedFragment));
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not write to fragment file", e);
            return false;
        }
    }

    private byte[] encryptFragment(byte[] data) throws Exception {
        Cipher cipher = getCipherEngine();
        SecretKey key = getEncryptionKey();

        byte[] iv = new byte[IV_LENGTH];
        getRandomGenerator().nextBytes(iv);
        IvParameterSpec cipherParams = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, key, cipherParams);
        cipher.updateAAD(iv);

        byte[] encryptedBytes = cipher.doFinal(data);
        byte[] fullData = new byte[IV_LENGTH + encryptedBytes.length];
        System.arraycopy(iv, 0, fullData, 0, IV_LENGTH);
        System.arraycopy(encryptedBytes, 0, fullData, IV_LENGTH, encryptedBytes.length);

        return fullData;
    }

    private byte[] decryptFragment(byte[] data) throws Exception {
        Cipher cipher = getCipherEngine();
        SecretKey key = getEncryptionKey();

        byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
        byte[] encryptedData = Arrays.copyOfRange(data, IV_LENGTH, data.length);
        IvParameterSpec cipherParams = new IvParameterSpec(iv);

        cipher.init(Cipher.DECRYPT_MODE, key, cipherParams);
        cipher.updateAAD(iv);

        return cipher.doFinal(encryptedData);
    }

    private MessageDigest getShortHashFunction() {
        try {
            return MessageDigest.getInstance(HASH_SCHEME_SHORT, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid hash algorithm ["+e.getLocalizedMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid hash algorithm provider ["+e.getLocalizedMessage()+"]");
        }
    }

    private SecretKey getEncryptionKey() {
        try {
            SecretKeyFactory kdf = SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
            PBEKeySpec keySpec = new PBEKeySpec(new String(mpDerivative, StandardCharsets.UTF_8).toCharArray(),
                    new byte[]{0x00},
                    KDF_ITERATIONS,
                    KDF_LENGTH);
            return kdf.generateSecret(keySpec);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid KDF algorithm ["+e.getMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid KDF algorithm provider ["+e.getMessage()+"]");
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid KDF spec ["+e.getMessage()+"]");
        }
    }

    private Cipher getCipherEngine() {
        try {
            return Cipher.getInstance(CIPHER_SCHEME);
        } catch (NoSuchPaddingException e) {
            throw new IllegalStateException("Invalid cipher padding ["+e.getMessage()+"]");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid cipher algorithm ["+e.getMessage()+"]");
        }
    }

    private byte[] generatePasswordDerivative(String password) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(),
                    new byte[]{0x00},
                    KDF_ITERATIONS,
                    KDF_LENGTH);
            SecretKey key = factory.generateSecret(keySpec);
            return key.getEncoded();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid KDF algorithm ["+e.getMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid KDF algorithm provider ["+e.getMessage()+"]");
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Bad KDF spec ["+e.getMessage()+"]");
        }
    }
}
