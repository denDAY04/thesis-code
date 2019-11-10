package edu.dk.asj.dpm.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.DatagramChannel;

public class NodeDiscoveryListener extends Thread implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeDiscoveryListener.class);

    private DatagramChannel channel;

    @Override
    public void run() {
        super.run();
    }

    @Override
    public void close() throws Exception {
        cleanUp();
    }

    private void cleanUp() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close channel", e);
            }
        }
    }
}
