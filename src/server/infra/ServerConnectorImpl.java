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
        System.out.println("[RÉPLICA] Enviando a " + (backupServers.size() - 1) + " backup(s)...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            executor.submit(() -> {
                try {
                    // Enviar con timeout
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            info.getStub().applyReplication(fullDocument, clockSnapshot);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    
                    future.get(3000, TimeUnit.MILLISECONDS);
                    System.out.println("  ✓ Réplica enviada a servidor " + info.getServerId());
                    
                } catch (TimeoutException e) {
                    System.out.println("  ⏱️  Timeout enviando a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("  ✗ Error enviando a servidor " + info.getServerId() + ": " + e.getMessage());
                }
            });
        }
    }

    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }
}