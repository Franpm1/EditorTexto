package server.core;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CompletableFuture;
import server.infra.*;

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
        // Log reducido para velocidad
        System.out.println("Op: " + op.getType() + " de " + op.getOwner());
        
        if (serverState.isLeader()) {
            // 1. Aplicar localmente
            document.applyOperation(op);
            
            // 2. Broadcast PARALELO a clientes locales
            CompletableFuture.runAsync(() -> {
                notifier.broadcast(document.getContent(), document.getClockCopy());
            }, ServerMain.GLOBAL_EXECUTOR);
            
            // 3. Replicar PARALELO a backups
            if (backupConnector != null) {
                backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
            }
        } else {
            // Redirigir al líder EN SEGUNDO PLANO
            CompletableFuture.runAsync(() -> {
                RemoteServerInfo leaderInfo = findLeaderInfo();
                if (leaderInfo != null) {
                    try {
                        leaderInfo.getStub().executeOperation(op);
                    } catch (Exception e) {
                        // Fallback rápido: aplicar localmente
                        document.applyOperation(op);
                        notifier.broadcast(document.getContent(), document.getClockCopy());
                    }
                } else {
                    // No hay líder conocido - aplicar localmente
                    document.applyOperation(op);
                    notifier.broadcast(document.getContent(), document.getClockCopy());
                }
            }, ServerMain.GLOBAL_EXECUTOR);
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
        
        // Enviar estado actual EN SEGUNDO PLANO
        CompletableFuture.runAsync(() -> {
            try {
                client.syncState(document.getContent(), document.getClockCopy());
            } catch (RemoteException e) {
                // Cliente no disponible
            }
        }, ServerMain.GLOBAL_EXECUTOR);
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Respuesta inmediata
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Recibiendo traspaso de liderazgo...");
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        System.out.println("Ahora soy el líder.");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("📢 RECIBIDO: Servidor " + leaderId + " se ha declarado LÍDER.");
        
        // CRÍTICO: Solo aceptar si el nuevo líder tiene ID mayor que el actual
        int currentLeader = serverState.getCurrentLeaderId();
        if (currentLeader != -1 && leaderId <= currentLeader) {
            System.out.println("⚠️  IGNORANDO: " + leaderId + " no es mayor que líder actual " + currentLeader);
            return;
        }
        
        // Aceptar nuevo líder
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
        
        if (leaderId == serverState.getMyServerId()) {
            System.out.println("🎉 ¡Confirmado! YO soy el nuevo líder.");
        } else {
            System.out.println("✅ Aceptado nuevo líder: servidor " + leaderId);
            
            // Si tenía contenido local, descartarlo (el líder tiene la verdad)
            try {
                // Opcional: pedir estado al nuevo líder
                RemoteServerInfo newLeader = findLeaderInfo();
                if (newLeader != null) {
                    DocumentSnapshot snapshot = newLeader.getStub().getCurrentState();
                    document.overwriteState(snapshot.getContent(), snapshot.getClock());
                    System.out.println("Estado sincronizado con nuevo líder.");
                }
            } catch (Exception e) {
                // Ignorar error
            }
        }
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        // Aplicar réplica y broadcast PARALELO
        document.overwriteState(doc, clock);
        
        CompletableFuture.runAsync(() -> {
            notifier.broadcast(document.getContent(), document.getClockCopy());
        }, ServerMain.GLOBAL_EXECUTOR);
    }

    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }
}