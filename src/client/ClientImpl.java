package client;

import common.IClientCallback;
import common.VectorClock;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ClientImpl extends UnicastRemoteObject implements IClientCallback {
    private final ConsoleUI ui;

    public ClientImpl(ConsoleUI ui) throws RemoteException {
        super();
        this.ui = ui;
    }

    @Override
    public void syncState(String fullDocument, VectorClock clock) throws RemoteException {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("SINCRONIZACIÓN RECIBIDA DEL SERVIDOR");
        System.out.println("Contenido: " + (fullDocument.isEmpty() ? "(vacío)" : fullDocument));
        System.out.println("Vector Clock: " + clock);
        System.out.println("=".repeat(50));
        
        // Actualizar la interfaz
        ui.updateView(fullDocument, clock);
    }
}