package edu.dk.asj.dpm.network.packets;

/**
 * Interface for handing a network packet and errors that may occur while receiving a packet or its response.
 */
public interface PacketHandler {

    /**
     * Handle a received packet and possibly return a response to be sent.
     * @param request the request packet to process.
     * @return the packet to send as reply, if any. If no reply is needed, return null.
     */
    Packet process(Packet request);

    /**
     * Handle an error that was raised during the reception or transmission of the packets.
     * @param error the error message.
     */
    void error(String error);
}
