package edu.dk.asj.dpm;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Singleton managing the application's different security schemes.
 */
public class SecurityScheme {

    private static final String RANDOM_GENERATOR_SCHEME = "DRBG";

    private static SecurityScheme instance;

    private SecurityScheme() {
        verifySchemes();
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
     * Retrieve an instance of the scheme's secure random number generator.
     * @return a secure random instance.
     */
    public SecureRandom getRandomGenerator() {
        try {
            return SecureRandom.getInstance(RANDOM_GENERATOR_SCHEME);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Could not retrieve SecureRandom instance");
            return null;
        }
    }

    private void verifySchemes() {
        try {
            SecureRandom.getInstance(RANDOM_GENERATOR_SCHEME);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Invalid random generator");
            e.printStackTrace();
        }
    }
}
