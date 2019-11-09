package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.requests.Packet;
import edu.dk.asj.dpm.network.requests.UserAuthPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class NetworkConnectionTest {

    private static Packet testRequest;
    private static Packet testResponse;

    @Test
    @DisplayName("Client can send request to server")
    void connectionTest() throws InterruptedException {
        AssertionPacketHandler handler = new AssertionPacketHandler();
        int serverPort = 47201;
        testRequest = new UserAuthPacket(50142);

        ServerNetworkConnection server = ServerNetworkConnection.open(handler, serverPort);
        ClientNetworkConnection client = ClientNetworkConnection.send(testRequest, new InetSocketAddress("localhost", serverPort));

        server.join();
        client.join();

        assertNull(client.getError(), "Client failed with error: " + client.getError());
        assertNotNull(client.getResponse(), "Client did not receive response");
        assertEquals(testResponse, client.getResponse(), "Unexpected response");
    }


    private static class AssertionPacketHandler implements RequestHandler{

        @Override
        public Packet process(Packet request) {
            assertSame(request.getClass(), testRequest.getClass(), "Unexpected request class");

            assertEquals(testRequest, request);

            testResponse = new UserAuthPacket(14570);
            return testResponse;
        }

        @Override
        public void error(String error) {
            fail("HANDLER ERROR: " + error);
        }
    }
}