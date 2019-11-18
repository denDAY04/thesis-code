package edu.dk.asj.dpm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;

import static org.junit.jupiter.api.Assertions.*;

class NetworkInterfaceHelperTest {

    @Test
    @DisplayName("Get active network interface controller")
    void getActiveNetInterface() {
        NetworkInterface nic = NetworkInterfaceHelper.getNetworkInterfaceController();
        assertNotNull(nic, "Controller is null");
    }

    @Test
    @DisplayName("Get active network interface controller address")
    void getActiveNetInterfaceAddr() {
        InetAddress address = NetworkInterfaceHelper.getNetworkInterfaceAddress();
        assertNotNull(address, "Address is null");
    }
}