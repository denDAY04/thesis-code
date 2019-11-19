package edu.dk.asj.dpm.security;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

/**
 * A wrapper for the SAE parameters and secret primitives  needed to perform the SAE authentication protocol. This
 * class <i>is not</i> intended to be transmitted to another node participating in the protocol as it contains
 * sensitive data that should not be exposed outside of the node generating them.
 * <br>
 * For SAE parameters that are safe for transmission, see {@link SAEParameterSpec}.
 */
public class SAESession {
    private final SAEParameterSpec parameters;
    private final BigInteger rand;
    private final ECPoint pwe;
    private BigInteger sharedKey;

    /**
     * Construct a new SAE session.
     * @param parameters the public parameters.
     * @param rand the secret random value.
     * @param pwe the secret password element.
     */
    SAESession(SAEParameterSpec parameters, BigInteger rand, ECPoint pwe) {
        this.parameters = parameters;
        this.rand = rand;
        this.pwe = pwe;
    }

    /**
     * Get the public session parameters fit for in-the-clear transmission.
     * @return the parameters.
     */
    SAEParameterSpec getParameters() {
        return parameters;
    }

    /**
     * Get the SAE rand value. This value is a secret primitive.
     * @return the rand value.
     */
    BigInteger getRand() {
        return rand;
    }

    /**
     * Get the password element. This value is a secret primitive.
     * @return the password element.
     */
    ECPoint getPwe() {
        return pwe;
    }

    /**
     * Get the shared key. This value is a secret primitive.
     * @return the shared key.
     */
    BigInteger getSharedKey() {
        return sharedKey;
    }

    /**
     * Set the shared key.
     * @param sharedKey the shared key.
     */
    void setSharedKey(BigInteger sharedKey) {
        this.sharedKey = sharedKey;
    }
}
