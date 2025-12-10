package server.core;

import common.IEditorService;
import common.IClientCallback;
import common.Operation;
import common.VectorClock;
import server.infra.BullyElection;
import server.infra.Notifier;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementa RMI (IEditorService) y también el proveedor de datos para Bully.
 */
public class EditorServiceImpl extends UnicastRemoteObject 
        implements IEditorService, BullyElection.EditorDocumentProvider {

    private final Document document;
    private final Notifier notifier;
    private final int myId;

    public EditorServiceImpl(int myId, Document doc, Notifier notifier) throws RemoteException {
        super();
        this.myId = myId;
        this.document = doc;
        this.notifier = notifier;
    }

    // --- LÓGICA RMI (CLIENTES) ---

    @Override
    public void writeOperation(Operation op) throws RemoteException {
        //Aplicar lógica de negocio (concurrencia y relojes)
        document.applyOperation(op);

        //Difundir cambios
        //Notifier pide snapshot para enviar a todos
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    @Override
    public void registerClient(IClientCallback client) throws RemoteException {
        //pasa el estado inicial
        notifier.registerClient(client, document.getContent(), document.getClockCopy());
    }

    // --- LÓGICA RMI (INFRAESTRUCTURA) ---

    @Override
    public void heartbeat() throws RemoteException {
        //¿¿Estoy vivo??
    }

    @Override
    public void becomeLeader(String fullDocument, VectorClock newClock) throws RemoteException {
        System.out.println("[RMI] Recibido mandato de nuevo LÍDER. Sincronizando estado...");
        document.overwriteState(fullDocument, newClock);
    }

    // --- INTERFAZ INTERNA PARA BULLY (EditorDocumentProvider) ---
    // Esto permite que el algoritmo de elección lea nuestros datos sin romper el encapsulamiento

    @Override
    public String getDocumentSnapshot() {
        return document.getContent();
    }

    @Override
    public VectorClock getClockSnapshot() {
        return document.getClockCopy();
    }

    @Override
    public void onBecameLeader() {
        System.out.println("[CORE] ¡Soy el nuevo LÍDER! Aceptando escrituras.");    }
}