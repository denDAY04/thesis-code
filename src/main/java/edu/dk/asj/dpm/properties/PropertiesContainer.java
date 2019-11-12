package edu.dk.asj.dpm.properties;

import java.io.IOException;
import java.util.Properties;

/**
 * Container encapsulating all application properties in one object hierarchy. The properties are themselves read from
 * the <i>application.properties</i> file fetched from the classloader.
 */
public class PropertiesContainer {

    private static PropertiesContainer instance;
    private final StorageProperties storageProperties;

    private PropertiesContainer(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /**
     * Get the properties container.
     * @return the container.
     */
    public static PropertiesContainer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Properties not loaded");
        }
        return instance;
    }

    /**
     * Load properties from the <i>application.properties</i> file in the classloader.
     * @throws IOException if an error occurred when trying to read the file
     */
    public static void loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(PropertiesContainer.class.getResourceAsStream("/application.properties"));

        StorageProperties storageProperties = new StorageProperties(properties);

        instance = new PropertiesContainer(storageProperties);
    }

    /**
     * Get storage properties object.
     * @return the object.
     */
    public StorageProperties getStorageProperties() {
        return storageProperties;
    }
}
