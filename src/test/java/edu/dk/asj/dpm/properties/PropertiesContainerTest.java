package edu.dk.asj.dpm.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesContainerTest {

    @Test
    @DisplayName("Load properties from file")
    void loadProperties() throws IOException {
        PropertiesContainer.loadProperties();
        PropertiesContainer properties = PropertiesContainer.loadProperties();

        assertNotNull(properties, "Properties container is null");
        assertNotNull(properties.getStorageProperties(),"Storage properties is null");
        assertNotNull(properties.getStorageProperties().getFragmentPath(), "Fragment storage path is null");
        assertNotNull(properties.getStorageProperties().getNetworkPropertiesPath(), "Network properties storage path is null");
    }
}