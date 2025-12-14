package server.infra;

import common.VectorClock;
import java.util.List;
import java.util.concurrent.*;

public class ServerConnectorImpl implements IServerConnector {
    private final List<RemoteServerInfo> backupServers;
    private final int myId;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> backupServers) {
        this.myId = myId;
        this.backupServers = backupServers;
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        // Versión por defecto: sin broadcast en backups
        propagateToBackups(fullDocument, clockSnapshot, false);
    }
    
    // NUEVO: con control de broadcast
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot, boolean askForBroadcast) {
        System.out.println("[RÉPLICA] Enviando a backups (broadcast=" + askForBroadcast + ")...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            executor.submit(() -> {
                try {
                    // Enviamos la réplica
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    System.out.println("  ✓ Réplica enviada a servidor " + info.getServerId());
                    
                } catch (Exception e) {
                    System.out.println("  ✗ Réplica falló para servidor " + info.getServerId() + ": " + e.getMessage());
                }
            });
        }
    }

    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }
}