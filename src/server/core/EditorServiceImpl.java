package server.core;

import common.*;
import server.infra.*;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class EditorServiceImpl extends UnicastRemoteObject implements IEditorService {

    private final Document document;
    private final Notifier notifier;
    private IServerConnector backupConnector;
    private final ServerState serverState;

    public EditorServiceImpl(Document doc, Notifier notifier, ServerState state) throws RemoteException {
        super();
        this.document = doc;
        this.notifier = notifier;
        this.serverState = state;
    }

    public void setBackupConnector(IServerConnector sc) {
        this.backupConnector = sc;
    }

    @Override
    public void executeOperation(Operation op) throws RemoteException {
        System.out.println("Operacion recibida de cliente: " + op.getType() + " de " + op.getOwner());

        // SI SOY LÍDER: procesar y replicar
        if (serverState.isLeader()) {
            System.out.println("Soy lider, procesando operacion...");

            // 0) WAL (durabilidad) ANTES de tocar memoria
            try {
                document.appendToWAL(op);
            } catch (IOException e) {
                throw new RemoteException("Fallo escribiendo WAL. Rechazo operación para no perder durabilidad.", e);
            }

            // 1) Aplicar localmente
            document.applyOperation(op);

            // 2) Broadcast a mis clientes locales
            notifier.broadcast(document.getContent(), document.getClockCopy());

            // 3) Replicar a backups
            if (backupConnector != null) {
                backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
            }

            // 4) Snapshot periódico (limpia WAL)
            try {
                if (document.shouldSnapshot()) {
                    System.out.println("[PERSIST] Snapshot por umbral.");
                    document.saveSnapshot();
                }
            } catch (IOException e) {
                System.out.println("[PERSIST] Error guardando snapshot: " + e.getMessage());
            }
        }
        // SI NO SOY LÍDER: redirigir al líder SIN aplicar localmente
        else {
            System.out.println("No soy lider, redirigiendo operacion al líder...");

            RemoteServerInfo leaderInfo = findLeaderInfo();

            if (leaderInfo != null) {
                try {
                    leaderInfo.getStub().executeOperation(op);
                    System.out.println("Operacion redirigida al líder " + serverState.getCurrentLeaderId());
                } catch (Exception e) {
                    System.out.println("Error redirigiendo al líder: " + e.getMessage());
                    System.out.println("NO aplico localmente: espero a nuevo líder / reconexión.");
                    throw new RemoteException("No se pudo redirigir al líder.", e);
                }
            } else {
                System.out.println("No se encontro al líder (leaderId=" + serverState.getCurrentLeaderId() + ")");
                throw new RemoteException("No hay líder conocido ahora mismo.");
            }
        }
    }

    private RemoteServerInfo findLeaderInfo() {
        if (backupConnector instanceof ServerConnectorImpl) {
            ServerConnectorImpl connector = (ServerConnectorImpl) backupConnector;
            for (RemoteServerInfo info : connector.getAllServers()) {
                if (info.getServerId() == serverState.getCurrentLeaderId()) {
                    return info;
                }
            }
        }
        return null;
    }

    @Override
    public void registerClient(IClientCallback client, String username) throws RemoteException {
        System.out.println("Registrando cliente: " + username);
        notifier.registerClient(client);
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Simple respuesta de que estoy vivo
    }

    /**
     * En vuestro contrato actual, esta firma existe.
     * Para evitar "líderes falsos", aquí lo tratamos como SYNC de estado completo.
     * El liderazgo real se define con declareLeader().
     */
    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("becomeLeader(): Recibiendo estado completo del líder...");
        document.overwriteState(doc, clock);

        // Persistencia: snapshot tras recibir estado completo
        try {
            document.saveSnapshot();
        } catch (IOException e) {
            System.out.println("[PERSIST] Error snapshot tras becomeLeader: " + e.getMessage());
        }

        // NO me marco líder aquí.
        serverState.setLeader(false);

        // Aviso a mis clientes locales si tengo
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("Servidor " + leaderId + " se ha declarado LIDER.");
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Replicacion recibida del líder");

        document.overwriteState(doc, clock);

        // Persistencia en backup: snapshot + limpia WAL
        try {
            document.saveSnapshot();
        } catch (IOException e) {
            System.out.println("[PERSIST] Error snapshot en backup: " + e.getMessage());
        }

        // Broadcast a mis clientes locales
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }
}
