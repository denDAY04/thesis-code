package dk.asj.dpm.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
class NetworkPropertiesTest {

    private static final String STORAGE_PATH = "./test-data/network.properties";
    private static final BigInteger NETWORK_ID = BigInteger.ONE;
    private static final Long NETWORK_ID_SEED = 42L;

    @Test
    @Order(1)
    @DisplayName("Generate network properties")
    void generate() {
        NetworkProperties properties = NetworkProperties.generate(NETWORK_ID, NETWORK_ID_SEED, STORAGE_PATH);

        assertNotNull(properties, "Network properties is null");
        assertNotNull(properties.getNodeId(), "Node ID is null");
        assertEquals(NETWORK_ID, properties.getNetworkId(), "Unexpected network ID");
        assertEquals(NETWORK_ID_SEED, properties.getNetworkIdSeed(), "Unexpected network ID seed");
    }

    @Test
    @Order(2)
    @DisplayName("Load from file")
    void loadFromFile() {
        NetworkProperties properties = NetworkProperties.loadFromStorage(STORAGE_PATH);

        assertNotNull(properties, "Network properties is null");
        assertNotNull(properties.getNodeId(), "Node ID is null");
        assertEquals(NETWORK_ID, properties.getNetworkId(), "Unexpected network ID");
        assertEquals(NETWORK_ID_SEED, properties.getNetworkIdSeed(), "Unexpected network ID seed");
    }

    @Test
    @DisplayName("Load from invalid file returns null")
    void loadFromInvalidFile() {
        NetworkProperties properties = NetworkProperties.loadFromStorage(STORAGE_PATH + "/invalid");

        assertNull(properties, "Network properties is not null");
    }
}