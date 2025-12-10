package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IEditorService extends Remote {
    
    // --- MÉTODOS PARA CLIENTES (Frontend) ---
    void executeOperation(Operation op) throws RemoteException;
    void registerClient(IClientCallback client, String username) throws RemoteException;

    // --- MÉTODOS PARA INFRAESTRUCTURA (Servidor a Servidor) ---
    
    // Llamado por HeartbeatMonitor para verificar si el nodo sigue vivo
    void heartbeat() throws RemoteException;

    // Llamado por BullyElection cuando un nodo gana la elección y nos avisa
    // Recibimos el estado más reciente para sincronizarnos
    void becomeLeader(String docSnapshot, VectorClock clockSnapshot) throws RemoteException;
    
    // Llamado por el Líder para replicar cambios a los backups
    void applyReplication(String content, VectorClock clock) throws RemoteException;
}