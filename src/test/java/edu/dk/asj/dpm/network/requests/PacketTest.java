package edu.dk.asj.dpm.network.requests;

import edu.dk.asj.dpm.vault.VaultFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("UserAuthPacket I/O")
    void userAuthPacketIO() {
        UserAuthPacket userAuthPacket = new UserAuthPacket(1337);
        byte[] data = userAuthPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(userAuthPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(userAuthPacket, packet, "Packets are not equal");
    }
}