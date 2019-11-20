package edu.dk.asj.dpm.security;

import edu.dk.asj.dpm.util.StorageHelper;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.djb.Curve25519;
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
import java.util.UUID;

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

    private final ECCurve ec;

    private byte[] mpDerivative;


    private SecurityController() {
        Security.addProvider(new BouncyCastleProvider());

        getRandomGenerator();
        getHashFunction();
        getCipherEngine();

        try {
            SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid KDF algorithm ["+e.getMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid KDF algorithm provider ["+e.getMessage()+"]");
        }
        ec = new Curve25519();
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

        MessageDigest hashFunction = getHashFunction();
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
            byte[] data = decrypt(fileStream.readAllBytes(), mpDerivative);
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
            fileStream.write(encrypt(serializedFragment, mpDerivative));
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not write to fragment file", e);
            return false;
        }
    }

    /**
     * Initiate a session for the SAE authentication protocol, generating its parameters and secret primitives.
     * @param localNode the identity of this local node.
     * @param remoteNode the identity of the remote node.
     * @return the initiated SAE session. Note that the session does not yet have an initialized <i>sharedKey</i> value,
     * but all other fields are initialized.
     */
    public SAESession initiateSaeSession(UUID localNode, UUID remoteNode) {
        if (localNode.equals(remoteNode)) {
            throw new RuntimeException("Identities must not be the same");
        }

        BigInteger curvePrime = ec.getField().getCharacteristic();
        BigInteger curveOrder = ec.getOrder();

        SecureRandom rng = getRandomGenerator();
        MessageDigest hash = getHashFunction();

        ECPoint pwe = null;
        int i;
        int limit = 100000;
        for(i = 1; i <= limit && (pwe == null || pwe.isInfinity()); ++i) {
            if (localNode.compareTo(remoteNode) > 0) {
                hash.update(localNode.toString().getBytes(StandardCharsets.UTF_8));
                hash.update(remoteNode.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                hash.update(remoteNode.toString().getBytes(StandardCharsets.UTF_8));
                hash.update(localNode.toString().getBytes(StandardCharsets.UTF_8));
            }
            hash.update(mpDerivative);
            hash.update(Integer.toString(i).getBytes());
            byte[] pwdSeed = hash.digest();

            SecretKey key = generateSAEKey(pwdSeed);
            BigInteger x = new BigInteger(key.getEncoded()).mod(curvePrime);

            boolean flipSign = new BigInteger(pwdSeed).mod(BigInteger.TWO).equals(BigInteger.ONE);
            pwe = findPointForX(x, flipSign);
        }

        if (i > limit) {
            throw new RuntimeException("Could not calculate fitting curve point in "+limit+" cycles");
        } else {
            LOGGER.debug("Found valid password element after "+i+" iterations");
        }

        BigInteger rand = new BigInteger(curveOrder.bitLength(), rng);
        BigInteger mask = new BigInteger(curveOrder.bitLength(), rng);

        BigInteger scalar = rand.add(mask).mod(curveOrder);
        ECPoint element = pwe.multiply(mask).negate();
        SAEParameterSpec saeParameters = new SAEParameterSpec(scalar, element.getEncoded(false));

        return new SAESession(saeParameters, rand, pwe);
    }

    /**
     * Compute the local node's SAE verification token using the SAE session and the participating node's parameters.
     * @param session the current SAE session.
     * @param remoteParameters the SAE parameters of the remote participating node.
     * @return the verification token.
     */
    public byte[] generateSAEToken(SAESession session, SAEParameterSpec remoteParameters) {
        ECPoint localElem = ec.decodePoint(session.getParameters().getElem());
        BigInteger localScalar = session.getParameters().getScalar();
        ECPoint remoteElem = ec.decodePoint(remoteParameters.getElem());
        BigInteger remoteScalar = remoteParameters.getScalar();
        ECPoint pwe = session.getPwe();
        BigInteger rand = session.getRand();

        ECPoint secretElem = pwe.multiply(remoteScalar).add(remoteElem).multiply(rand);
        BigInteger sharedKey = bijectiveFunction(secretElem);
        session.setSharedKey(sharedKey);

        MessageDigest hashFunction = getHashFunction();
        hashFunction.update(sharedKey.toByteArray());
        hashFunction.update(bijectiveFunction(localElem).toByteArray());
        hashFunction.update(localScalar.toByteArray());
        hashFunction.update(bijectiveFunction(remoteElem).toByteArray());
        hashFunction.update(remoteScalar.toByteArray());

        return hashFunction.digest();
    }

    /**
     * Complete an SAE protocol session by validating a participating node's token, resulting in the generated
     * secret (encryption key) if successfull.
     * @param session the session parameters generated from calling
     *          {@link SecurityController#initiateSaeSession(UUID, UUID)}.
     * @param remoteToken the participating remote node's token.
     * @param remoteParameters the participating remote node's parameters.
     * @return if the validation is successful the result is the secret key shared between this node and the
     *          participating node. If the validation fails the result is null.
     */
    public byte[] validateSAEToken(SAESession session, byte[] remoteToken, SAEParameterSpec remoteParameters) {
        ECPoint localElem = ec.decodePoint(session.getParameters().getElem());
        BigInteger localScalar = session.getParameters().getScalar();
        ECPoint remoteElem = ec.decodePoint(remoteParameters.getElem());
        BigInteger remoteScalar = remoteParameters.getScalar();
        BigInteger sharedKey = session.getSharedKey();

        MessageDigest hashFunction = getHashFunction();
        hashFunction.update(sharedKey.toByteArray());
        hashFunction.update(bijectiveFunction(remoteElem).toByteArray());
        hashFunction.update(remoteScalar.toByteArray());
        hashFunction.update(bijectiveFunction(localElem).toByteArray());
        hashFunction.update(localScalar.toByteArray());
        byte[] remoteVerificationToken = hashFunction.digest();

        if (!Arrays.equals(remoteToken, remoteVerificationToken)) {
            return null;
        }

        hashFunction.update(sharedKey.toByteArray());
        hashFunction.update(bijectiveFunction(localElem.add(remoteElem)).toByteArray());
        hashFunction.update(localScalar.add(remoteScalar).mod(ec.getOrder()).toByteArray());
        return hashFunction.digest();
    }

    /**
     * Derive a secret key (for cipher encryption) from the base key such that the secret key <i>k = KDF(baseKey)</i>
     * @param baseKey the input key to derive a new key from.
     * @return the newly derived secret key.
     */
    public SecretKey deriveSecretKey(byte[] baseKey) {
        try {
            SecretKeyFactory kdf = SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
            PBEKeySpec keySpec = new PBEKeySpec(new String(baseKey, StandardCharsets.UTF_8).toCharArray(),
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

    /**
     * Encrypt the clear-text using a secret key derived from the base key.
     * @param clearText the data to be encrypted.
     * @param baseKey the key to use as input for a key derivation function such that the encryption key
     *                <i>k = KDF(baseKey)</i>
     * @return the resulting cipher-text.
     * @throws Exception if an error was raised.
     */
    public byte[] encrypt(byte[] clearText, byte[] baseKey) throws Exception {
        Cipher cipher = getCipherEngine();
        SecretKey key = deriveSecretKey(baseKey);

        byte[] iv = new byte[IV_LENGTH];
        getRandomGenerator().nextBytes(iv);
        IvParameterSpec cipherParams = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, cipherParams);

        byte[] cipherText = cipher.doFinal(clearText);

        byte[] fullData = new byte[IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, fullData, 0, IV_LENGTH);
        System.arraycopy(cipherText, 0, fullData, IV_LENGTH, cipherText.length);

        return fullData;
    }

    /**
     * Decrypt the cipher-text using a secret key derived from the base key.
     * @param cipherText the data to be decrypted.
     * @param baseKey the key to use as input for a key derivation function such that the encryption key
     *                <i>k = KDF(baseKey)</i>
     * @return the resulting clear-text.
     * @throws Exception if an error was raised.
     */
    public byte[] decrypt(byte[] cipherText, byte[] baseKey) throws Exception {
        Cipher cipher = getCipherEngine();
        SecretKey key = deriveSecretKey(baseKey);

        byte[] iv = Arrays.copyOfRange(cipherText, 0, IV_LENGTH);
        byte[] encryptedData = Arrays.copyOfRange(cipherText, IV_LENGTH, cipherText.length);

        IvParameterSpec cipherParams = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, cipherParams);

        return cipher.doFinal(encryptedData);
    }

    private BigInteger bijectiveFunction(ECPoint p) {
        byte[] encoded = p.getEncoded(false);
        // first byte of encoded point is a flag, so it's ignored
        return new BigInteger(encoded, 1, encoded.length - 1);
    }

    private MessageDigest getHashFunction() {
        try {
            return MessageDigest.getInstance(HASH_SCHEME_SHORT, "BC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid hash algorithm ["+e.getLocalizedMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid hash algorithm provider ["+e.getLocalizedMessage()+"]");
        }
    }

    private SecretKey generateSAEKey(byte[] pwdSeed) {
        try {
            SecretKeyFactory kdf = SecretKeyFactory.getInstance(KDF_SCHEME, "BC");
            KeySpec keySpec = new PBEKeySpec(
                    new String(pwdSeed, StandardCharsets.UTF_8).toCharArray(),
                    new byte[]{0x00},
                    KDF_ITERATIONS,
                    ec.getField().getCharacteristic().bitLength());     // big-length of curve's prime field
            return kdf.generateSecret(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid SAE KDF algorithm ["+e.getMessage()+"]");
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException("Invalid SAE KDF algorithm provider ["+e.getMessage()+"]");
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid SAE KDF spec ["+e.getMessage()+"]");
        }
    }

    private ECPoint findPointForX(BigInteger xCoordinate, boolean flipSign) {
        ECFieldElement xField = ec.fromBigInteger(xCoordinate);
        // find Y by elliptic curve equation y^2=x^3+ax+b
        ECFieldElement yField = xField.multiply(xField).multiply(xField)
                .add(ec.getA().multiply(xField))
                .add(ec.getB())
                .sqrt();

        if (yField == null) {
            return null;
        }

        try {
            return flipSign ? ec.validatePoint(xField.toBigInteger(), yField.negate().toBigInteger())
                    : ec.validatePoint(xField.toBigInteger(), yField.toBigInteger());
        } catch (IllegalArgumentException e) {
            //point was invalid
            return null;
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
