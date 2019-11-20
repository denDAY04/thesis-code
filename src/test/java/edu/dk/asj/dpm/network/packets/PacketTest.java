package edu.dk.asj.dpm.network.packets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PacketTest {

    @Test
    @DisplayName("Packet serialize/deserialize")
    void fragmentPacketIO() {
        Packet identityPacket = new IdentityPacket(UUID.randomUUID());

        byte[] data = identityPacket.serialize();
        assertNotNull(data, "Data array is null");

        Packet packet = Packet.deserialize(data);
        assertNotNull(packet, "De-serialized packet is null");
        assertSame(identityPacket.getClass(), packet.getClass(), "Unexpected de-serialized packet class");
        assertEquals(identityPacket, packet, "Packets are not equal");
    }
}