package dk.asj.dpm.network;

import dk.asj.dpm.network.requests.Request;

public interface RequestProcessor {

    void process(Request request);

    void error(String error);
}
