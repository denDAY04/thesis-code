package edu.dk.asj.dpm.network.packets;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Packet for use in authenticating a connection using the SAE (Simultaneous Authentication of Equals) scheme.
 * This packet encapsulates the first round of parameters needed in the authentication process: a scalar and an
 * Elliptic-Curve (EC) domain element's coordinate.
 */
public class SAEParameterPacket extends Packet {
    private static final long serialVersionUID = 8045661147118684303L;

    private final BigInteger scalar;
    private final BigInteger elementX;
    private final BigInteger elementY;

    /**
     * Construct a packet with a specific ECC (Elliptic-Curve Cryptography) parameters.
     * @param scalar a scalar.
     * @param elementX a curve domain element's X coordinate.
     * @param elementY a curve domain element's Y coordinate.
     */
    public SAEParameterPacket(BigInteger scalar, BigInteger elementX, BigInteger elementY) {
        Objects.requireNonNull(scalar, "Scalar must not be null");
        Objects.requireNonNull(elementX, "Element's X coordinate not be null");
        Objects.requireNonNull(elementY, "Element's Y coordinate must not be null");

        this.scalar = scalar;
        this.elementX = elementX;
        this.elementY = elementY;
    }

    /**
     * Get the scalar.
     * @return the scalar number.
     */
    public BigInteger getScalar() {
        return scalar;
    }

    /**
     * Get the curve domain element's X coordinate.
     * @return the X coordinate.
     */
    public BigInteger getElementX() {
        return elementX;
    }

    /**
     * Get the curve domain element's Y coordinate.
     * @return the Y coordinate.
     */
    public BigInteger getElementY() {
        return elementY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SAEParameterPacket)) return false;
        SAEParameterPacket that = (SAEParameterPacket) o;
        return scalar.equals(that.scalar) &&
                elementX.equals(that.elementX) &&
                elementY.equals(that.elementY);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scalar, elementX, elementY);
    }
}
