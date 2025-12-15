package server.infra;

import common.VectorClock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServerConnectorImpl implements IServerConnector {
    private final List<RemoteServerInfo> backupServers;
    private final int myId;
    private final ExecutorService replicaPool;

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> backupServers) {
        this.myId = myId;
        this.backupServers = backupServers;
        // Configurar timeouts RMI globales
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "1000");
        System.setProperty("sun.rmi.transport.tcp.readTimeout", "1000");
        System.setProperty("sun.rmi.transport.connectionTimeout", "1000");
        System.setProperty("sun.rmi.transport.proxy.connectTimeout", "1000");
        System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "1000");
        
        this.replicaPool = Executors.newFixedThreadPool(backupServers.size());
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            replicaPool.execute(() -> {
                try {
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                } catch (Exception e) {
                    // Silencioso
                    System.err.println("❌ ERROR replicando a Servidor " + info.getServerId() + ": " + e.getMessage());
                }
            });
        }
    }

    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }

    public void shutdown() {
        replicaPool.shutdown();
        try {
            if (!replicaPool.awaitTermination(1, TimeUnit.SECONDS)) {
                replicaPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            replicaPool.shutdownNow();
        }
    }
}