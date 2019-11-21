package edu.dk.asj.dpm.properties;

import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.util.StorageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkProperties.class);

    private final UUID nodeId;
    private final BigInteger networkId;
    private final String networkIdSeed;

    private NetworkProperties(UUID nodeId, BigInteger networkId, String networkIdSeed) {
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
    public String getNetworkIdSeed() {
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
            LOGGER.debug("Could not read net properties", e);
            return null;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Net properties data was corrupted", e);
            return null;
        }
    }

    /**
     * Generate the complete network properties for this node. The properties object is saved to the indicated file
     * before it is returned.
     * @param masterPassword the user's master password
     * @param networkIdSeed seed used in generating the network ID.
     * @param storagePath the full file path where the network properties will be saved.
     * @return the generated network properties, or null if the properties could not be saved.
     */
    public static NetworkProperties generate(String masterPassword, String networkIdSeed, String storagePath) {
        if (masterPassword == null || masterPassword.isBlank()) {
            throw new IllegalArgumentException("Master password must not be null or blank");
        }
        if (networkIdSeed == null || networkIdSeed.isBlank()) {
            throw new IllegalArgumentException("Network ID seed must not be null or blank");
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Storage path must not be null or blank");
        }

        BigInteger networkId = SecurityController.getInstance().computeNetworkId(masterPassword, networkIdSeed);

        UUID nodeId = UUID.randomUUID();
        NetworkProperties properties = new NetworkProperties(nodeId, networkId, networkIdSeed);
        Path path;
        try {
            path = StorageHelper.getOrCreateStoragePath(storagePath);
        } catch (IOException e) {
            LOGGER.warn("Could not get or create storage path", e);
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
            LOGGER.error("Could not create network properties file", e);
            return null;
        } catch (IOException e) {
            LOGGER.error("Could not write to network properties file", e);
            return null;
        }
    }

}
