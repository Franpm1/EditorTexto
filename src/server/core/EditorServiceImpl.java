package server.core;

import common.IClientCallback;
import common.IEditorService;
import common.Operation;
import common.VectorClock;

import server.infra.BullyElection;
import server.infra.IServerConnector;
import server.infra.Notifier;
import server.infra.ServerState;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class EditorServiceImpl extends UnicastRemoteObject
        implements IEditorService, BullyElection.StateProvider {

    private final Document document;
    private final Notifier notifier;
    private final ServerState serverState;
    private final IServerConnector serverConnector;

    public EditorServiceImpl(Document document,
                             Notifier notifier,
                             IServerConnector serverConnector,
                             ServerState serverState) throws RemoteException {
        super();
        this.document = document;
        this.notifier = notifier;
        this.serverConnector = serverConnector;
        this.serverState = serverState;
    }

    // =========================
    // ===== CLIENTES ==========
    // =========================

    @Override
    public void registerClient(IClientCallback client, String username) throws RemoteException {
        System.out.println("Cliente conectado: " + username);
        notifier.registerClient(client);

        // Estado actual al conectarse
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void executeOperation(Operation op) throws RemoteException {
        if (!serverState.isLeader()) {
            System.out.println("[CORE] Soy BACKUP: ignoro operación de cliente.");
            return;
        }

        // 1) WAL: durabilidad antes de aplicar
        try {
            document.appendToWAL(op);
        } catch (IOException e) {
            throw new RemoteException("No se pudo escribir WAL. Rechazo operación para mantener durabilidad.", e);
        }

        // 2) aplicar operación
        document.applyOperation(op);

        // 3) replicar a backups (estado completo) (fire & forget)
        serverConnector.propagateToBackups(document.getContent(), document.getClockCopy());

        // 4) broadcast a clientes locales
        notifier.broadcast(document.getContent(), document.getClockCopy());

        // 5) snapshot cada N operaciones
        try {
            if (document.shouldSnapshot()) {
                System.out.println("[PERSIST] Snapshot por umbral. Limpio WAL.");
                document.saveSnapshot(); // también clearWAL()
            }
        } catch (IOException e) {
            System.out.println("[PERSIST] Error guardando snapshot: " + e.getMessage());
        }
    }

    // =========================
    // ===== INFRA =============
    // =========================

    @Override
    public void heartbeat() throws RemoteException {
        // Responder => estoy vivo
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        // Soy backup: estado completo del líder
        document.overwriteState(doc, clock);

        // Persistencia: estado completo recibido => snapshot + clearWAL
        try {
            document.saveSnapshot();
            System.out.println("[PERSIST] Backup guardó snapshot tras applyReplication.");
        } catch (IOException e) {
            System.out.println("[PERSIST] Error snapshot en backup: " + e.getMessage());
        }

        // Actualizar clientes locales conectados a este servidor
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        serverState.setCurrentLeaderId(leaderId);
        boolean iAmLeader = (leaderId == serverState.getMyServerId());
        serverState.setLeader(iAmLeader);

        System.out.println("[CORE] Nuevo líder: " + leaderId + " | Yo soy líder? " + iAmLeader);
    }

    /**
     * Semántica recomendada:
     * - becomeLeader(doc, clock) = "el líder me manda el estado completo"
     *   (útil en recuperación/re-sync).
     *
     * Si en vuestra versión actual lo usáis de otra manera, decidlo,
     * pero con esta semántica NO se crean líderes falsos.
     */
    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("[CORE] becomeLeader(): recibo estado completo del líder.");

        document.overwriteState(doc, clock);

        // Persistencia: snapshot + clearWAL
        try {
            document.saveSnapshot();
            System.out.println("[PERSIST] Snapshot tras becomeLeader.");
        } catch (IOException e) {
            System.out.println("[PERSIST] Error snapshot tras becomeLeader: " + e.getMessage());
        }

        // Yo NO me marco líder aquí.
        serverState.setLeader(false);

        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    // =========================
    // ===== Provider local para BullyElection ==========
    // =========================

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
        System.out.println("[CORE] He ganado la elección: soy líder.");

        // Recomendado: snapshot inmediato al ganar para estabilidad tras failover
        try {
            document.saveSnapshot();
            System.out.println("[PERSIST] Snapshot forzado al convertirse en líder.");
        } catch (IOException e) {
            System.out.println("[PERSIST] Error snapshot al convertirse en líder: " + e.getMessage());
        }
    }
}
