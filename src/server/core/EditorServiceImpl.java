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
    
    // Control para evitar bucles y duplicados
    private String lastProcessedOperation = "";
    private long lastOperationTime = 0;

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
        String operationId = op.getOwner() + ":" + op.getType() + ":" + op.getPosition() + ":" + op.getText();
        long currentTime = System.currentTimeMillis();
        
        // Evitar procesar la misma operación dos veces
        if (operationId.equals(lastProcessedOperation) && (currentTime - lastOperationTime) < 1000) {
            System.out.println("⏭️  Operación duplicada, ignorando: " + operationId);
            return;
        }
        
        lastProcessedOperation = operationId;
        lastOperationTime = currentTime;
        
        System.out.println("📨 Operación recibida de " + op.getOwner() + ": " + op.getType() + " pos=" + op.getPosition());
        
        if (serverState.isLeader()) {
            // *** SÓLO EL LÍDER procesa operaciones de clientes ***
            System.out.println("👑 Soy líder, procesando y replicando...");
            
            // 1. Aplicar localmente
            document.applyOperation(op);
            System.out.println("✓ Aplicado localmente. Documento: " + 
                (document.getContent().isEmpty() ? "(vacío)" : document.getContent().length() + " chars"));
            
            // 2. Broadcast a MIS clientes locales
            notifier.broadcast(document.getContent(), document.getClockCopy());
            System.out.println("📢 Notificado a mis " + notifier.getClientCount() + " cliente(s) locales");
            
            // 3. Réplica a backups (para que ellos también notifiquen a SUS clientes)
            if (backupConnector != null) {
                System.out.println("🔄 Replicando a backups...");
                backupConnector.propagateToBackups(
                    document.getContent(), 
                    document.getClockCopy()
                );
            }
        } 
        else {
            // *** BACKUP: redirigir al líder ***
            System.out.println("🔄 Soy backup (ID " + serverState.getMyServerId() + "), redirigiendo al líder " + serverState.getCurrentLeaderId());
            
            RemoteServerInfo leaderInfo = findLeaderInfo();
            
            if (leaderInfo != null) {
                try {
                    // Timeout corto para redirección
                    CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            leaderInfo.getStub().executeOperation(op);
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    });
                    
                    boolean success = future.get(2000, TimeUnit.MILLISECONDS);
                    
                    if (success) {
                        System.out.println("✓ Operación redirigida al líder");
                        // NO aplicar localmente - esperar réplica del líder
                    } else {
                        throw new Exception("Redirección falló");
                    }
                    
                } catch (TimeoutException e) {
                    System.out.println("⚠️ Timeout redirigiendo al líder, aplicando localmente");
                    applyOperationLocally(op);
                } catch (Exception e) {
                    System.out.println("⚠️ Error redirigiendo: " + e.getMessage() + ", aplicando localmente");
                    applyOperationLocally(op);
                }
            } else {
                System.out.println("⚠️ No se encontró líder, aplicando localmente (modo emergencia)");
                applyOperationLocally(op);
            }
        }
    }
    
    private void applyOperationLocally(Operation op) {
        document.applyOperation(op);
        notifier.broadcast(document.getContent(), document.getClockCopy());
        System.out.println("✓ Aplicado localmente en backup");
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
        System.out.println("👤 Cliente registrado en servidor " + serverState.getMyServerId() + ": " + username);
        notifier.registerClient(client);
        
        // Enviar estado actual INMEDIATAMENTE
        client.syncState(document.getContent(), document.getClockCopy());
        System.out.println("✓ Estado enviado al nuevo cliente");
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Respuesta inmediata
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("🎯 Recibiendo liderazgo con estado sincronizado...");
        System.out.println("  Estado recibido: " + (doc.isEmpty() ? "(vacío)" : doc.length() + " caracteres"));
        
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        
        // CRÍTICO: Notificar a MIS clientes locales del nuevo estado
        notifier.broadcast(document.getContent(), document.getClockCopy());
        System.out.println("✅ Ahora soy líder - " + notifier.getClientCount() + " cliente(s) notificado(s)");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("📢 Nuevo líder declarado: servidor " + leaderId);
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
        
        if (serverState.isLeader()) {
            System.out.println("⚠️ ¡Yo soy el nuevo líder! (esto no debería pasar aquí)");
        }
    }

    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        // *** RÉPLICA DEL LÍDER: aplicar Y notificar a clientes locales ***
        System.out.println("🔄 Recibiendo réplica del líder...");
        System.out.println("  Estado replicado: " + (doc.isEmpty() ? "(vacío)" : doc.length() + " caracteres"));
        
        // Verificar si ya tenemos este estado
        String currentContent = document.getContent();
        if (currentContent.equals(doc)) {
            System.out.println("⏭️  Estado idéntico al actual, ignorando réplica");
            return;
        }
        
        // Aplicar el estado replicado
        document.overwriteState(doc, clock);
        System.out.println("✓ Estado aplicado en backup");
        
        // *** IMPORTANTE: Notificar a NUESTROS clientes locales ***
        // Esto NO crea bucle porque:
        // 1. El líder ya notificó a SUS clientes
        // 2. Nosotros notificamos a NUESTROS clientes
        // 3. No reenviamos a otros servidores
        notifier.broadcast(document.getContent(), document.getClockCopy());
        System.out.println("📢 " + notifier.getClientCount() + " cliente(s) local(es) notificado(s)");
    }
    
    // Para debugging
    public void debugStatus() {
        System.out.println("\n=== DEBUG Servidor " + serverState.getMyServerId() + " ===");
        System.out.println("Líder: " + serverState.isLeader());
        System.out.println("Líder actual: " + serverState.getCurrentLeaderId());
        System.out.println("Documento: '" + document.getContent() + "'");
        System.out.println("Longitud: " + document.getContent().length() + " chars");
        System.out.println("Clientes: " + notifier.getClientCount());
        System.out.println("====================\n");
    }
}