package server.core;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;
import server.infra.*;

public class EditorServiceImpl extends UnicastRemoteObject implements IEditorService {

    private final Document document;
    private final Notifier notifier;
    private IServerConnector backupConnector;
    private final ServerState serverState;
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        System.out.println("Operación recibida de " + op.getOwner() + ": " + op.getType());
        
        if (serverState.isLeader()) {
            // LÍDER: procesar localmente inmediatamente
            document.applyOperation(op);
            notifier.broadcast(document.getContent(), document.getClockCopy());
            
            // Réplica en segundo plano (no bloquea)
            if (backupConnector != null) {
                backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
            }
        } 
        else {
            // BACKUP: redirigir con TIMEOUT
            System.out.println("Redirigiendo operación al líder...");
            
            RemoteServerInfo leaderInfo = findLeaderInfo();
            
            if (leaderInfo != null) {
                // Intentar redirección con timeout
                CompletableFuture<Boolean> redirectFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        leaderInfo.getStub().executeOperation(op);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }, executor);
                
                try {
                    // Timeout corto: 1.5 segundos
                    boolean success = redirectFuture.get(1500, TimeUnit.MILLISECONDS);
                    
                    if (success) {
                        System.out.println("✓ Operación redirigida al líder");
                        return; // Éxito, no hacer nada más
                    }
                } catch (TimeoutException e) {
                    System.out.println("⚠️ Timeout al redirigir al líder");
                } catch (Exception e) {
                    System.out.println("⚠️ Error al redirigir: " + e.getMessage());
                }
            }
            
            // Fallback: aplicar localmente si el líder no responde
            System.out.println("Aplicando operación localmente (fallback)");
            document.applyOperation(op);
            notifier.broadcast(document.getContent(), document.getClockCopy());
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
        System.out.println("Cliente registrado: " + username);
        notifier.registerClient(client);
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Respuesta inmediata
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Recibiendo liderazgo con sincronización...");
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        System.out.println("✅ Ahora soy el líder");
        
        // CRÍTICO: Notificar a mis clientes locales INMEDIATAMENTE
        notifier.broadcast(document.getContent(), document.getClockCopy());
        System.out.println("📢 Clientes locales notificados del nuevo estado");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("Nuevo líder declarado: servidor " + leaderId);
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
        
        // Si yo ERA el líder anterior y ahora otro es líder, debo notificar a mis clientes
        // que ya no soy líder y que redirijan sus operaciones
        if (serverState.getMyServerId() != leaderId && serverState.isLeader()) {
            // Esto no debería pasar, pero por seguridad
            serverState.setLeader(false);
        }
    }

    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        // Respuesta INMEDIATA sin procesamiento
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        // Aplicar réplica inmediatamente
        document.overwriteState(doc, clock);
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }
}