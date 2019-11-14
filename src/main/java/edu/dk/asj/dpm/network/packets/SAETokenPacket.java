package edu.dk.asj.dpm.network.packets;

import java.util.Arrays;
import java.util.Objects;

/**
 * Packet for use in authenticating a connection using the SAE (Simultaneous Authentication of Equals) scheme.
 * This packet encapsulates the second round of parameters needed in the authentication process: an
 * authentication/verification token.
 */
public class SAETokenPacket extends Packet {
    private static final long serialVersionUID = -1333257494477722021L;

    private final byte[] token;

    /**
     * Construct a token packet with the given authentication token.
     * @param token the token.
     */
    public SAETokenPacket(byte[] token) {
        Objects.requireNonNull(token, "Token must not be null");
        this.token = token;
    }

    /**
     * Get the authentication token.
     * @return the token.
     */
    public byte[] getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SAETokenPacket)) return false;
        SAETokenPacket that = (SAETokenPacket) o;
        return Arrays.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(token);
    }
}
