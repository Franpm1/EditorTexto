package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * LA INTERFAZ DEL SERVIDOR (RMI)
 * Define qué métodos pueden invocar remotamente los Clientes y otros Servidores.
 */
public interface IEditorService extends Remote {
    
    // --- PARTE 1: Para la Pareja A (Clientes) ---

    /**
     * El cliente envía una operación (tecla pulsada + su reloj).
     * @param op El objeto con los datos y el 'clientClock'.
     */
    void writeOperation(Operation op) throws RemoteException;
    
    /**
     * El cliente se conecta para recibir actualizaciones en tiempo real.
     */
    void registerClient(IClientCallback client) throws RemoteException;
    

    // --- PARTE 2: Para la Pareja C (Infraestructura / Backups) ---

    /**
     * Heartbeat: Los backups llaman aquí para ver si sigues vivo.
     */
    void ping() throws RemoteException; 
	
    /**
     * Replicación: Cuando un servidor necesita copiar el estado de otro.
     */
    void syncState(String fullDocument, VectorClock newClock) throws RemoteException;
    
    /**
     * Algoritmo Bully: Alguien convoca elecciones.
     */
    void startElection(int candidateId) throws RemoteException; 

    /**
     * Algoritmo Bully: Habemus nuevo líder.
     */
    void sendCoordinator(int leaderId) throws RemoteException;
}