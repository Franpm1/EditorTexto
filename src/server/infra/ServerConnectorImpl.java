package server.infra;

import common.VectorClock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        System.out.println("Replicando a backups (en paralelo)...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            executor.submit(() -> {
                try {
                    System.out.println("Enviando a servidor " + info.getServerId() + "...");
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    System.out.println("Replicado a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("Servidor " + info.getServerId() + " no disponible");
                }
            });
        }
    }

    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }
}