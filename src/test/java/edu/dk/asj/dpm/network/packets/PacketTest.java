package edu.dk.asj.dpm.network.packets;

import edu.dk.asj.dpm.vault.VaultFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PacketTest {

    @Test
    @DisplayName("FragmentPacket I/O")
    void fragmentPacketIO() {
        VaultFragment fragment = new VaultFragment(new int[]{0}, new byte[]{0x01}, 1);
        FragmentPacket fragmentPacket = new FragmentPacket(fragment);

        byte[] data = fragmentPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(fragmentPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(fragmentPacket, packet, "Packets are not equal");
    }

    @Test
    @DisplayName("DiscoveryPacket I/O")
    void discoveryPacketIO() {
        DiscoveryPacket discoveryEchoPacket = new DiscoveryPacket(BigInteger.valueOf(1337L));
        byte[] data = discoveryEchoPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(discoveryEchoPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(discoveryEchoPacket, packet, "Packets are not equal");
    }

    @Test
    @DisplayName("DiscoveryEchoPacket I/O")
    void discoveryEchoPacketIO() {
        DiscoveryEchoPacket discoveryEchoPacket = new DiscoveryEchoPacket(1337);
        byte[] data = discoveryEchoPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(discoveryEchoPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(discoveryEchoPacket, packet, "Packets are not equal");
    }

    @Test
    @DisplayName("IdentityPacket I/O")
    void IdentityPacketIO() {
        IdentityPacket identityPacket = new IdentityPacket(UUID.randomUUID());
        byte[] data = identityPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(identityPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(identityPacket, packet, "Packets are not equal");
    }

    @Test
    @DisplayName("SAEParameterPacket I/O")
    void SAEParameterPacketIO() {
        SAEParameterPacket saeParamPacket = new SAEParameterPacket(BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN);
        byte[] data = saeParamPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(saeParamPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(saeParamPacket, packet, "Packets are not equal");
    }

    @Test
    @DisplayName("SAETokenPacket I/O")
    void SAETokenPacketIO() {
        byte[] token = new byte[]{0x07, 0x38};
        SAETokenPacket saeTokenPacket = new SAETokenPacket(token);
        byte[] data = saeTokenPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(saeTokenPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(saeTokenPacket, packet, "Packets are not equal");
    }
}