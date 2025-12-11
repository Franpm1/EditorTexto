package server.infra;
import common.VectorClock;
public interface IServerConnector {
    void propagateToBackups(String fullDocument, VectorClock clockSnapshot);
}