package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.DiscoveryPacket;
import edu.dk.asj.dpm.network.packets.Packet;
import edu.dk.asj.dpm.network.packets.DiscoveryEchoPacket;
import edu.dk.asj.dpm.util.BufferHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.junit.jupiter.api.Assertions.*;

class NetworkConnectionTest {

    private static Packet testClientRequest;
    private static Packet testServerResponse;

    private static BigInteger networkId;
    private static Packet discoveryEcho;

    @Test
    @DisplayName("Client can communicate with server")
    void connectionTest() throws InterruptedException {
        ServerPacketHandler handler = new ServerPacketHandler();
        int serverPort = 47201;
        testClientRequest = new DiscoveryPacket(BigInteger.ONE);

        ServerConnection server = ServerConnection.open(handler, serverPort);
        ClientConnection client = ClientConnection.send(testClientRequest, new InetSocketAddress("localhost", serverPort));

        server.join();
        client.join();

        assertNull(client.getError(), "Client failed with error: " + client.getError());
        assertNotNull(client.getResponse(), "Client did not receive response");
        assertEquals(testServerResponse, client.getResponse(), "Unexpected response");
    }

    @Test
    @DisplayName("Discovery listener")
    void discoveryTest() throws IOException {
        networkId = BigInteger.ONE;
        DiscoveryListener listener = DiscoveryListener.start(new DiscoveryHandler(), networkId);

        DatagramChannel testChannel = DatagramChannel.open(StandardProtocolFamily.INET);
        SocketAddress receiver = new InetSocketAddress(DiscoveryListener.PEER_GROUP_ADDRESS, DiscoveryListener.PEER_GROUP_PORT);
        DiscoveryPacket packet = new DiscoveryPacket(networkId);
        testChannel.send(ByteBuffer.wrap(packet.serialize()), receiver);

        ByteBuffer receiverBuffer = ByteBuffer.allocate(512);
        testChannel.receive(receiverBuffer);
        Packet response = Packet.deserialize(BufferHelper.readAndClear(receiverBuffer));

        assertNotNull(response, "Discovery echo if null");
        assertEquals(discoveryEcho, response, "Unexpected discovery echo");
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
        public Packet process(Packet request, SocketAddress sender) {
            return null;
        }

        @Override
        public void error(String error) {
            fail("HANDLER ERROR: " + error);
        }
    }


    private static class DiscoveryHandler implements PacketHandler<DiscoveryPacket> {

        @Override
        public Packet process(DiscoveryPacket request) {
            assertEquals(networkId, request.getNetworkId(), "Unexpected network ID");
            discoveryEcho = new DiscoveryEchoPacket(1337);
            return discoveryEcho;
        }

        @Override
        public Packet process(DiscoveryPacket request, SocketAddress sender) {
            return null;
        }

        @Override
        public void error(String error) {
            fail("Discovery handler error: " + error);
        }
    }
}