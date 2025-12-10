package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * LA INTERFAZ DEL CLIENTE (CALLBACK)
 * Permite al servidor enviar cambios a la pantalla del usuario (Push).
 */
public interface IClientCallback extends Remote {
    
    /**
     * El servidor llama a esto cuando el documento cambia.
     * @param document El texto completo nuevo.
     * @param serverClock El reloj vectorial del servidor (para que el cliente actualice el suyo).
     */
    void updateDocument(String document, VectorClock serverClock) throws RemoteException; //antes updateView, cambiado para adaptarme a Notifier
}