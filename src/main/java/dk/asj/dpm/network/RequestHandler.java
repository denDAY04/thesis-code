package dk.asj.dpm.network;

import dk.asj.dpm.network.requests.Packet;

public interface RequestHandler {

    Packet process(Packet request);

    void error(String error);
}
