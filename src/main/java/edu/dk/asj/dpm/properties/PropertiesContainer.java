package edu.dk.asj.dpm.properties;

import java.io.IOException;
import java.util.Properties;

/**
 * Container encapsulating all application properties in one object hierarchy. The properties are themselves read from
 * the <i>application.properties</i> file fetched from the classloader.
 */
public class PropertiesContainer {

    private final StorageProperties storageProperties;

    private PropertiesContainer(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /**
     * Load properties from the <i>application.properties</i> file in the classloader.
     * @throws IOException if an error occurred when trying to read the file
     * @return the properties container.
     */
    public static PropertiesContainer loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(PropertiesContainer.class.getResourceAsStream("/application.properties"));

        StorageProperties storageProperties = new StorageProperties(properties);

        return new PropertiesContainer(storageProperties);
    }

    /**
     * Get storage properties object.
     * @return the object.
     */
    public StorageProperties getStorageProperties() {
        return storageProperties;
    }
}
