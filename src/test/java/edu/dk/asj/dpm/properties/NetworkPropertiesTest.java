package edu.dk.asj.dpm.properties;

import edu.dk.asj.dpm.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
class NetworkPropertiesTest {

    private static final String STORAGE_PATH = "./test-data/network.properties";
    private static final String PASSWORD = "123456";
    private static final String NETWORK_ID_SEED = "1337";

    @Test
    @Order(1)
    @DisplayName("Generate network properties")
    void generate() {

        NetworkProperties properties = NetworkProperties.generate(PASSWORD, NETWORK_ID_SEED, STORAGE_PATH);

        assertNotNull(properties, "Network properties is null");
        assertNotNull(properties.getNodeId(), "Node ID is null");
        assertEquals(NETWORK_ID_SEED, properties.getNetworkIdSeed(), "Unexpected network ID seed");
        assertEquals(generateNetworkId(), properties.getNetworkId(), "Unexpected network ID");
    }

    @Test
    @Order(2)
    @DisplayName("Load from file")
    void loadFromFile() {
        NetworkProperties properties = NetworkProperties.loadFromStorage(STORAGE_PATH);

        assertNotNull(properties, "Network properties is null");
        assertNotNull(properties.getNodeId(), "Node ID is null");
        assertEquals(generateNetworkId(), properties.getNetworkId(), "Unexpected network ID");
        assertEquals(NETWORK_ID_SEED, properties.getNetworkIdSeed(), "Unexpected network ID seed");
    }

    @Test
    @DisplayName("Load from invalid file returns null")
    void loadFromInvalidFile() {
        NetworkProperties properties = NetworkProperties.loadFromStorage(STORAGE_PATH + "/invalid");
        assertNull(properties, "Network properties is not null");
    }

    private BigInteger generateNetworkId() {
        MessageDigest hashFunction = SecurityScheme.getInstance().getHashFunction();
        hashFunction.update(PASSWORD.getBytes(StandardCharsets.UTF_8));
        byte[] mpHash = hashFunction.digest();

        hashFunction.update(mpHash);
        hashFunction.update(NETWORK_ID_SEED.getBytes(StandardCharsets.UTF_8));
        return new BigInteger(hashFunction.digest());
    }
}