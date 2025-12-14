package client;

import common.IClientCallback;
import common.VectorClock;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ClientImpl extends UnicastRemoteObject implements IClientCallback {
    private final ConsoleUI ui;
    private String lastKnownContent = "";
    private VectorClock lastKnownClock = null;

    public ClientImpl(ConsoleUI ui) throws RemoteException {
        super();
        this.ui = ui;
    }

    @Override
    public void syncState(String fullDocument, VectorClock clock) throws RemoteException {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("SINCRONIZACIÓN RECIBIDA DEL SERVIDOR");
        System.out.println("Contenido: " + (fullDocument.isEmpty() ? "(vacío)" : 
            (fullDocument.length() > 50 ? fullDocument.substring(0, 50) + "..." : fullDocument)));
        System.out.println("Vector Clock: " + clock);
        System.out.println("=".repeat(50));
        
        // Guardar último estado conocido
        this.lastKnownContent = fullDocument;
        this.lastKnownClock = clock;
        
        // Actualizar la interfaz
        ui.updateView(fullDocument, clock);
    }
    
    // Método para obtener último estado (por si necesitas)
    public String getLastKnownContent() {
        return lastKnownContent;
    }
    
    public VectorClock getLastKnownClock() {
        return lastKnownClock;
    }
}