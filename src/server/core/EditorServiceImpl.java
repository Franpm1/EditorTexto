package server.core;

import common.IEditorService;
import common.IClientCallback;
import common.Operation;
import common.VectorClock;
import server.infra.BullyElection;
import server.infra.Notifier;
import server.infra.IServerConnector; // Asegúrate de tener esta interfaz

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class EditorServiceImpl extends UnicastRemoteObject 
        implements IEditorService, BullyElection.EditorDocumentProvider {

    private final Document document;
    private final Notifier notifier;
    private final int myId;

    // Referencia a la capa de infraestructura para replicación
    private IServerConnector backupConnector; 

    public EditorServiceImpl(int myId, Document doc, Notifier notifier) throws RemoteException {
        super();
        this.myId = myId;
        this.document = doc;
        this.notifier = notifier;
    }

    public void setBackupConnector(IServerConnector connector) {
        this.backupConnector = connector;
    }

    // --- LÓGICA RMI (CLIENTES) ---

    @Override
    public void executeOperation(Operation op) throws RemoteException {
        // 1. Aplicar lógica de negocio
        document.applyOperation(op);

        // 2. Difundir a CLIENTES conectados a este nodo
        notifier.broadcast(document.getContent(), document.getClockCopy());

        // 3. REPLICACIÓN A SERVIDORES (Backups)
        if (backupConnector != null) {
            backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
        } else {
            // Esto es normal si somos el único nodo o estamos en pruebas
            // System.out.println("[INFO] Sin conector de backups o lista vacía.");
        }
    }

    @Override
    public void registerClient(IClientCallback client, String username) throws RemoteException {
        System.out.println("[RMI] Nuevo cliente registrado: " + username);
        notifier.registerClient(client, username);
        // Sincronización inicial
        client.syncState(document.getContent());
    }

    // --- MÉTODOS DE INFRAESTRUCTURA (Servidor a Servidor) ---

    @Override
    public void heartbeat() throws RemoteException {
        // Solo responder "estoy vivo" retornando sin error.
    }

    @Override
    public void becomeLeader(String docSnapshot, VectorClock clockSnapshot) throws RemoteException {
        System.out.println("[RMI] Notificación de LÍDER externo recibida. Sincronizando...");
        applyReplication(docSnapshot, clockSnapshot);
    }

    @Override
    public void applyReplication(String content, VectorClock clock) throws RemoteException {
        // Sobrescribimos nuestro estado local con el del líder
        document.overwriteState(content, clock);
        // Opcional: Si tuviéramos clientes de solo lectura conectados, les avisamos
        notifier.broadcast(content, clock);
    }

    // --- INTERFAZ LOCAL (Para BullyElection) ---

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
        System.out.println("[CORE] ¡He ganado la elección! Soy el nuevo LÍDER.");
    }
}