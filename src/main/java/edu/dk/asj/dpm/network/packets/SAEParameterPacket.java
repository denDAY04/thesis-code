package edu.dk.asj.dpm.network.packets;

import edu.dk.asj.dpm.security.SAEParameterSpec;

import java.util.Objects;

/**
 * Packet for use in authenticating a connection using the SAE (Simultaneous Authentication of Equals) scheme.
 * This packet encapsulates the first round of parameters needed in the authentication process.
 */
public class SAEParameterPacket extends Packet {
    private static final long serialVersionUID = 8045661147118684303L;

   private final SAEParameterSpec parameters;

    /**
     * Construct a packet with a specific ECC (Elliptic-Curve Cryptography) parameters.
     * @param saeParameterSpec the SAE scheme's parameters.
     */
    public SAEParameterPacket(SAEParameterSpec saeParameterSpec) {
        Objects.requireNonNull(saeParameterSpec, "SAE parameters must not be null");
        this.parameters = saeParameterSpec;
    }

    /**
     * Get the parameters container.
     * @return the parameters.
     */
    public SAEParameterSpec getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SAEParameterPacket)) return false;
        SAEParameterPacket that = (SAEParameterPacket) o;
        return parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters);
    }

    @Override
    public String toString() {
        return "SAEParameterPacket{parameters:"+ parameters +"}";
    }
}
