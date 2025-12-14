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
            // REDIRECCIÓN SÍNCRONA - El cliente debe saber si falla
            RemoteServerInfo leaderInfo = findLeaderInfo();
            if (leaderInfo != null) {
                try {
                    leaderInfo.getStub().executeOperation(op);
                    System.out.println("✓ Operación redirigida al líder " + leaderInfo.getServerId());
                } catch (Exception e) {
                    // Líder no disponible - aplicar localmente como fallback
                    System.out.println("✗ Líder no disponible, aplicando localmente");
                    document.applyOperation(op);
                    notifier.broadcast(document.getContent(), document.getClockCopy());
                    
                    // Iniciar elección en segundo plano
                    triggerElectionAsync();
                }
            } else {
                // No hay líder conocido - aplicar localmente
                System.out.println("⚠️  No hay líder conocido, aplicando localmente");
                document.applyOperation(op);
                notifier.broadcast(document.getContent(), document.getClockCopy());
                
                // Iniciar elección en segundo plano
                triggerElectionAsync();
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
    
    private void triggerElectionAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // Esperar 1s antes de elección
                if (!serverState.isLeader() && serverState.getCurrentLeaderId() == -1) {
                    System.out.println("🚨 Iniciando elección por falta de líder...");
                    // Esto debería dispararse a través del HeartbeatMonitor
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, ServerMain.GLOBAL_EXECUTOR);
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
        
        // **CORRECCIÓN CRÍTICA:** Solo verificar que no soy yo mismo el líder actual
        // No comparar IDs, aceptar cualquier declaración de líder (evita bloqueos)
        int currentLeader = serverState.getCurrentLeaderId();
        
        if (leaderId == serverState.getMyServerId()) {
            // Alguien me declara líder a mí - verificar consistencia
            if (!serverState.isLeader()) {
                System.out.println("⚠️  Me declaran líder pero yo no me considero líder. Sincronizando...");
                // Pedir estado al que me declara líder (debería ser yo mismo en elección)
            }
            return;
        }
        
        // Aceptar nuevo líder inmediatamente
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(false); // Yo no soy líder a menos que sea mi ID
        
        if (serverState.getMyServerId() > leaderId) {
            // **CORRECCIÓN:** Si tengo ID mayor, debo iniciar elección
            System.out.println("⚡ Yo tengo ID mayor (" + serverState.getMyServerId() + 
                             " > " + leaderId + "). Iniciando contra-elección...");
            triggerCounterElection(leaderId);
        } else {
            System.out.println("✅ Aceptado nuevo líder: servidor " + leaderId);
            
            // Sincronizar estado con el nuevo líder
            syncWithNewLeader(leaderId);
        }
    }
    
    private void triggerCounterElection(int currentLeaderId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500); // Pequeña espera
                if (serverState.getCurrentLeaderId() == currentLeaderId) {
                    System.out.println("🚀 Iniciando elección por tener ID mayor...");
                    // Notificar al HeartbeatMonitor o BullyElection
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, ServerMain.GLOBAL_EXECUTOR);
    }
    
    private void syncWithNewLeader(int leaderId) {
        CompletableFuture.runAsync(() -> {
            try {
                RemoteServerInfo newLeader = findLeaderInfo();
                if (newLeader != null && newLeader.getServerId() == leaderId) {
                    DocumentSnapshot snapshot = newLeader.getStub().getCurrentState();
                    document.overwriteState(snapshot.getContent(), snapshot.getClock());
                    System.out.println("Estado sincronizado con nuevo líder " + leaderId);
                }
            } catch (Exception e) {
                System.out.println("No se pudo sincronizar con líder " + leaderId + ": " + e.getMessage());
            }
        }, ServerMain.GLOBAL_EXECUTOR);
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