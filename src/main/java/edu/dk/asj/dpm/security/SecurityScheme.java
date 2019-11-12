package edu.dk.asj.dpm.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;

/**
 * Singleton managing the application's different security schemes.
 */
public class SecurityScheme {

    private static final String RANDOM_GENERATOR_SCHEME = "DRBG";
    private static final String HASH_SCHEME = "SHA3-512";

    private static SecurityScheme instance;

    private byte[] mpHash;


    private SecurityScheme() {
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
    public static SecurityScheme getInstance() {
        if (instance == null) {
            instance = new SecurityScheme();
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
     * Get the user's master password verification hash.
     * @return the has.
     */
    public byte[] getMpHash() {
        return mpHash;
    }

    /**
     * Set the master password verification hash.
     * @param hash the hash
     */
    public void setMpHash(byte[] hash) {
        mpHash = hash;
    }
}
