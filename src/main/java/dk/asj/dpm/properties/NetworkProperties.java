package dk.asj.dpm.properties;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Class encapsulating this node's network properties, which include the ID of the node-network this node is part of,
 * the seed used in generating the network ID, and the node's own ID.
 * <p>
 *     The node ID is a simple UUID that is generated when the properties are initially generated.
 * </p>
 */
public class NetworkProperties implements Serializable {
    private static final long serialVersionUID = 1581193987638702547L;

    private final UUID nodeId;
    private final BigInteger networkId;
    private final Long networkIdSeed;

    private NetworkProperties(UUID nodeId, BigInteger networkId, Long networkIdSeed) {
        this.nodeId = nodeId;
        this.networkId = networkId;
        this.networkIdSeed = networkIdSeed;
    }

    /**
     * Get this node's ID.
     * @return the node ID.
     */
    public UUID getNodeId() {
        return nodeId;
    }

    /**
     * Get the ID of the node-network this node is part of.
     * @return the network ID
     */
    public BigInteger getNetworkId() {
        return networkId;
    }

    /**
     * Get the seed used in generating the network ID.
     * @return the seed.
     */
    public Long getNetworkIdSeed() {
        return networkIdSeed;
    }

    /**
     * Load network properties from the given file.
     * @param storagePath the full file path of the file containing the network properties.
     * @return the read network properties, or null if no such file was found or the data was corrupted.
     */
    public static NetworkProperties loadFromStorage(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Storage path must not be null or blank");
        }

        StandardOpenOption[] fileOptions = new StandardOpenOption[] { READ };
        try (InputStream fileStream = Files.newInputStream(Paths.get(storagePath), fileOptions);
             ObjectInputStream objectStream = new ObjectInputStream(fileStream)) {

            return (NetworkProperties) objectStream.readObject();
        } catch (IOException e) {
            System.err.println("Network properties file not found");
            return null;
        } catch (ClassNotFoundException e) {
            System.err.println("Corrupted network properties file");
            return null;
        }
    }

    /**
     * Generate the complete network properties for this node. The properties object is written to the indicated file
     * before it is returned.
     * @param networkId ID of the node's network.
     * @param networkIdSeed seed used in generating the network ID.
     * @param storagePath the full file path where the network properties will be saved.
     * @return the generated network properties, or null if the properties could not be saved.
     */
    public static NetworkProperties generate(BigInteger networkId, Long networkIdSeed, String storagePath) {
        Objects.requireNonNull(networkId, "Network ID must not be null");
        Objects.requireNonNull(networkIdSeed, "Network ID seed must not be null");
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Storage path must not be null or blank");
        }

        UUID nodeId = UUID.randomUUID();
        NetworkProperties properties = new NetworkProperties(nodeId, networkId, networkIdSeed);

        Path path = getOrCreateStoragePath(storagePath);
        if (path == null) {
            return null;
        }
        return saveToStorage(properties, path);
    }

    private static NetworkProperties saveToStorage(NetworkProperties properties, Path path) {
        StandardOpenOption[] fileOptions = new StandardOpenOption[] { WRITE, TRUNCATE_EXISTING };
        try (OutputStream fileStream = Files.newOutputStream(path, fileOptions);
             ObjectOutputStream objectWriter = new ObjectOutputStream(fileStream)) {

            objectWriter.writeObject(properties);
            return properties;
        } catch (FileNotFoundException e) {
            System.err.println("Could not find network properties file");
            return null;
        } catch (IOException e) {
            System.err.println("Could not write to network properties file");
            return null;
        }
    }

    private static Path getOrCreateStoragePath(String storagePath) {
        Path path = Paths.get(storagePath);
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
            } catch (IOException e) {
                System.err.println("Could not create network properties file");
                return null;
            }
        }
        return path;
    }
}
