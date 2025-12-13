package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IEditorService extends Remote {
    // Operaciones del Editor (Cliente-Servidor)
    void executeOperation(Operation op) throws RemoteException;
    void registerClient(IClientCallback client, String username) throws RemoteException;
    
    // Operaciones entre Servidores
    void heartbeat() throws RemoteException;
    void applyReplication(String doc, VectorClock clock) throws RemoteException;
    void declareLeader(int leaderId) throws RemoteException;
    void becomeLeader(String doc, VectorClock clock) throws RemoteException; 
    
    // NUEVO: Para sincronización cuando un líder se recupera
    DocumentSnapshot getCurrentState() throws RemoteException;
}