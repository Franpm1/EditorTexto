package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
//LA INTERFAZ DEL SERVIDOR (RMI)
//métodos para Clientes y otros servidores.

public interface IEditorService extends Remote {
    
    /**
     * El cliente envía una operación (tecla pulsada + su reloj).
     * @param op El objeto con los datos y el 'clientClock'.
     */
    void writeOperation(Operation op) throws RemoteException;

    void registerClient(IClientCallback client) throws RemoteException;
    
    void heartbeat() throws RemoteException; //antes se llamaba ping, adaptado a HeartbeatMonitor.java
    /**
     * Replicación: Cuando un servidor necesita copiar el estado de otro.
     */
    void becomeLeader(String fullDocument, VectorClock newClock) throws RemoteException; //antes se llamaba syncState, adaptado a BullyElection.java
    
    /**
     * Algoritmo Bully: Alguien convoca elecciones.
    void startElection(int candidateId) throws RemoteException;
     * Algoritmo Bully: Habemus nuevo líder.
    void sendCoordinator(int leaderId) throws RemoteException;

    Esto no se usa, se usan timeouts en el HeartbeatMonitor.java
    */
}