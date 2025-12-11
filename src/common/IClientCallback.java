package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClientCallback extends Remote {
    // El servidor manda el estado completo y el reloj actual
    void syncState(String fullDocument, VectorClock clock) throws RemoteException;
}