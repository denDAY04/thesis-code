package edu.dk.asj.dpm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.NetworkInterface;

import static org.junit.jupiter.api.Assertions.*;

class NetworkInterfaceHelperTest {

    @Test
    @DisplayName("Get active network interface")
    void getActiveNetInterface() {
        NetworkInterface nic = NetworkInterfaceHelper.getActiveNetInterface();
        assertNotNull(nic, "Network interface is null");
    }
}