package dk.asj.dpm.properties;

import java.util.Objects;
import java.util.Properties;

/**
 * This class defines an object containing properties related to storage in the application.
 */
public class StorageProperties {

    private static final String PREFIX = "storage.path.";

    private final String fragmentPath;
    private final String networkPropertiesPath;

    /**
     * Construct the storage properties object by reading the relevant properties from the parameter object.
     * @param properties the properties object containing all application properties.
     */
    StorageProperties(Properties properties) {
        String fragmentPathKey = PREFIX + "vault-fragment";
        String networkPropsKey = PREFIX + "network-properties";

        this.fragmentPath = properties.getProperty(fragmentPathKey);
        this.networkPropertiesPath = properties.getProperty(networkPropsKey);

        Objects.requireNonNull(this.fragmentPath, "Missing property: " + fragmentPathKey);
        Objects.requireNonNull(this.networkPropertiesPath, "Missing property: " + networkPropsKey);
    }

    /**
     * Get storage path for the local vault fragment.
     * @return the storage path.
     */
    public String getFragmentPath() {
        return fragmentPath;
    }

    /**
     * Get storage path for the local network properties.
     * @return the storage path.
     */
    public String getNetworkPropertiesPath() {
        return networkPropertiesPath;
    }
}
