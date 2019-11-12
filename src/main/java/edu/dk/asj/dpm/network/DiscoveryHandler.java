package edu.dk.asj.dpm.network;

import edu.dk.asj.dpm.network.packets.Packet;

import java.net.SocketAddress;

public interface DiscoveryHandler {
    Packet process(Packet packet, SocketAddress sender);

    void error(String error);
}
