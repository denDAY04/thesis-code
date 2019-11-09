package dk.asj.dpm.network.requests;

import java.io.Serializable;
import java.util.Objects;

/**
 * Request containing a port number indicating the port a recipient of this packet should connect to in order to start
 * a user authentication process.
 */
public class UserAuthPacket extends Packet implements Serializable {
    private static final long serialVersionUID = 8379654888314823172L;

    private final int connectionPort;

    /**
     * Construct the request with a connection port for the receiver of this request to connect to.
     * @param connectionPort the connection port
     */
    public UserAuthPacket(int connectionPort) {
        this.connectionPort = connectionPort;
    }

    /**
     * Get the connection port.
     * @return the port.
     */
    public int getConnectionPort() {
        return connectionPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAuthPacket)) return false;
        UserAuthPacket that = (UserAuthPacket) o;
        return connectionPort == that.connectionPort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionPort);
    }
}
