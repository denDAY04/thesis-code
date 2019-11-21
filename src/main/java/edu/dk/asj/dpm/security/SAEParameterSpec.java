package edu.dk.asj.dpm.security;

import org.bouncycastle.util.encoders.Hex;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Parameters used in the initial phase of the SAE authentication protocol and is intended to bed transmitted to
 * another node participating in a protocol.
 *
 * The parameters include
 * <ul>
 *     <li>elem - a point on the elliptic curve underlying the scheme, in its encoded byte-array form</li>
 *     <li>scalar - a scalar that is within the curve's order
 * </ul>
 */
public class SAEParameterSpec implements Serializable {
    private static final long serialVersionUID = -5310054848365111551L;

    private final BigInteger scalar;
    private final byte[] elem;

    /**
     * Construct a set of SAE parameters.
     * @param scalar the scalar value.
     * @param encodedElement the element value in its (uncompressed) encoded form.
     */
    SAEParameterSpec(BigInteger scalar, byte[] encodedElement) {
        this.scalar = scalar;
        this.elem = encodedElement;
    }

    /**
     * Get the scalar value.
     * @return the scalar.
     */
    BigInteger getScalar() {
        return scalar;
    }

    /**
     * Get the (uncompressed) encoded element.
     * @return the element.
     */
    byte[] getElem() {
        return elem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SAEParameterSpec)) return false;
        SAEParameterSpec that = (SAEParameterSpec) o;
        return scalar.equals(that.scalar) &&
                Arrays.equals(elem, that.elem);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(scalar);
        result = 31 * result + Arrays.hashCode(elem);
        return result;
    }

    @Override
    public String toString() {
        return SAEParameterSpec.class + "{scalar:"+scalar+";elem:"+ Hex.toHexString(elem)+"}";
    }
}
