package server.infra; // o package server;

import common.VectorClock;

public interface IServerConnector {
    void propagateToBackups(String fullDocument, VectorClock clockSnapshot);
}