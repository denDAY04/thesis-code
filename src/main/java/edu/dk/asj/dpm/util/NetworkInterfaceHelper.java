package edu.dk.asj.dpm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;

public class NetworkInterfaceHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkInterfaceHelper.class);

    /**
     * Get the active network interface found on the host system.
     * @return the active network interface, or null if an error occurred or no active interface could
     * be determined.
     */
    public static NetworkInterface getActiveNetInterface() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
                if (networkInterface.isLoopback()) {
                    continue;
                }

                if (!networkInterface.isUp()) {
                    continue;
                }

                for (InetAddress netAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (netAddress instanceof Inet6Address) {
                        continue;
                    }

                    if (!netAddress.isReachable(3000)){
                        continue;
                    }

                    try (SocketChannel socket = SocketChannel.open()) {
                        socket.socket().setSoTimeout(3000);
                        socket.socket().setReuseAddress(true);

                        // bind the socket to your local interface
                        int upperBound = 60000;
                        int port = new Random().nextInt(upperBound) + 2000;
                        socket.bind(new InetSocketAddress(netAddress, port));

                        // try to connect to *somewhere*
                        socket.connect(new InetSocketAddress("google.com", 80));
                    } catch (IOException ex) {
                        LOGGER.debug("Could not connect with interface {}} using address {}}", networkInterface, netAddress);
                        continue;
                    }
                    LOGGER.debug("Using network interface {}", networkInterface);
                    return networkInterface;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Exception while determining network interface", e);
        }
        LOGGER.error("Found no fitting interface");
        return null;
    }
}
