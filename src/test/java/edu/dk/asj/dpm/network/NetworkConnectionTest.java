package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class NetworkConnectionTest {

    private static Packet testClientRequest;
    private static Packet testServerResponse;

    private static BigInteger networkId;
    private static Packet discoveryEcho;

    @Test
    @DisplayName("Client can communicate with server")
    void connectionTest() throws InterruptedException {
        ServerPacketHandler handler = new ServerPacketHandler();
        testClientRequest = new DiscoveryPacket(BigInteger.ONE);

        ServerConnection server = ServerConnection.open(handler);
        ClientConnection client = ClientConnection.send(testClientRequest, new InetSocketAddress("localhost", server.getPort()));

        server.join();
        client.join();

        assertNull(client.getError(), "Client failed with error: " + client.getError());
        assertNotNull(client.getResponse(), "Client did not receive response");
        assertEquals(testServerResponse, client.getResponse(), "Unexpected response");
    }


    private static class ServerPacketHandler implements PacketHandler {

        @Override
        public Packet process(Packet request) {
            assertSame(request.getClass(), testClientRequest.getClass(), "Unexpected request class");

            assertEquals(testClientRequest, request);

            testServerResponse = new DiscoveryEchoPacket(14570);
            return testServerResponse;
        }

        @Override
        public void error(String error) {
            fail("HANDLER ERROR: " + error);
        }
    }
}