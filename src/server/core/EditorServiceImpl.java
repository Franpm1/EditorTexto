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
    
    // Control para evitar bucles
    private boolean isApplyingReplication = false;
    private String lastReplicationHash = "";

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
        System.out.println("📨 Operación recibida de " + op.getOwner() + ": " + op.getType() + " pos=" + op.getPosition());
        
        if (serverState.isLeader()) {
            // *** SÓLO EL LÍDER procesa operaciones de clientes ***
            System.out.println("👑 Soy líder, procesando operación...");
            
            // 1. Aplicar localmente
            document.applyOperation(op);
            
            // 2. Broadcast SOLO a mis clientes locales
            notifier.broadcast(document.getContent(), document.getClockCopy());
            System.out.println("📢 Broadcast a mis " + notifier.getClientCount() + " clientes locales");
            
            // 3. Réplica a backups (pero NO les digas que hagan broadcast)
            if (backupConnector != null) {
                backupConnector.propagateToBackups(
                    document.getContent(), 
                    document.getClockCopy(),
                    false // ¡IMPORTANTE! No pedir broadcast a backups
                );
            }
        } 
        else {
            // *** BACKUP: redirigir al líder SIN procesar localmente ***
            System.out.println("🔄 Soy backup, redirigiendo al líder...");
            
            RemoteServerInfo leaderInfo = findLeaderInfo();
            
            if (leaderInfo != null) {
                try {
                    leaderInfo.getStub().executeOperation(op);
                    System.out.println("✓ Redirigido al líder " + serverState.getCurrentLeaderId());
                } catch (Exception e) {
                    System.out.println("⚠️ Error redirigiendo: " + e.getMessage());
                    // Fallback: sólo si el líder NO responde
                    if (shouldApplyLocallyAsFallback()) {
                        document.applyOperation(op);
                        notifier.broadcast(document.getContent(), document.getClockCopy());
                    }
                }
            } else {
                System.out.println("⚠️ No hay líder, aplicando localmente (modo emergencia)");
                document.applyOperation(op);
                notifier.broadcast(document.getContent(), document.getClockCopy());
            }
        }
    }

    private boolean shouldApplyLocallyAsFallback() {
        // Sólo aplicar localmente si no hemos tenido líder por un tiempo
        return serverState.getCurrentLeaderId() == -1;
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
        System.out.println("👤 Cliente registrado: " + username);
        notifier.registerClient(client);
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Respuesta inmediata
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("🎯 Recibiendo liderazgo con estado sincronizado...");
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        
        // CRÍTICO: Notificar a MIS clientes locales del nuevo estado
        notifier.broadcast(document.getContent(), document.getClockCopy());
        System.out.println("✅ Ahora soy líder - clientes notificados");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("📢 Nuevo líder declarado: servidor " + leaderId);
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
    }

    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        // *** ESTE ES EL CAMBIO CLAVE ***
        // Réplica del líder: aplicar PERO NO hacer broadcast
        
        if (isApplyingReplication) {
            System.out.println("⏸️  Ya estoy aplicando réplica, ignorando duplicado");
            return;
        }
        
        String replicationHash = doc + clock.toString();
        if (lastReplicationHash.equals(replicationHash)) {
            System.out.println("⏸️  Réplica duplicada, ignorando");
            return;
        }
        
        isApplyingReplication = true;
        try {
            System.out.println("🔄 Recibiendo réplica del líder...");
            
            // Aplicar el estado
            document.overwriteState(doc, clock);
            System.out.println("✓ Estado replicado: " + 
                (doc.isEmpty() ? "(vacío)" : doc.length() + " caracteres"));
            
            lastReplicationHash = replicationHash;
            
            // *** NO HACER BROADCAST - los clientes ya fueron notificados por el líder ***
            // Si haces broadcast aquí, crearás un bucle
            
        } finally {
            isApplyingReplication = false;
        }
    }
    
    // Método para debugging
    public void printStatus() {
        System.out.println("=== STATUS Servidor " + serverState.getMyServerId() + " ===");
        System.out.println("Es líder: " + serverState.isLeader());
        System.out.println("Líder actual: " + serverState.getCurrentLeaderId());
        System.out.println("Documento: " + document.getContent().length() + " chars");
        System.out.println("Clientes locales: " + notifier.getClientCount());
    }
}