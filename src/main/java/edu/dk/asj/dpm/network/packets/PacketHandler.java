package edu.dk.asj.dpm.network.packets;

import edu.dk.asj.dpm.network.packets.Packet;

public interface PacketHandler<P extends Packet> {

    Packet process(P request);

    void error(String error);
}
