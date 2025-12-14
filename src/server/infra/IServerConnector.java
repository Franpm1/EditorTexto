package server.infra;
import common.VectorClock;
public interface IServerConnector {
    void propagateToBackups(String fullDocument, VectorClock clockSnapshot);
    
    // NUEVO: versión con control de broadcast
    void propagateToBackups(String fullDocument, VectorClock clockSnapshot, boolean askForBroadcast);
}