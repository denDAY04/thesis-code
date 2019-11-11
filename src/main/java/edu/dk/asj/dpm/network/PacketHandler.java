package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.Packet;

import java.net.SocketAddress;

public interface PacketHandler<P extends Packet> {

    Packet process(P request);
    Packet process(P request, SocketAddress sender);

    void error(String error);
}
