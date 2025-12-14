package server.infra;

import common.VectorClock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerConnectorImpl implements IServerConnector {
    private final List<RemoteServerInfo> backupServers;
    private final int myId;
    private final ExecutorService replicaPool;

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> backupServers) {
        this.myId = myId;
        this.backupServers = backupServers;
        // Crear ThreadPool con tamaño fijo igual al número de backups máximos
        this.replicaPool = Executors.newFixedThreadPool(backupServers.size());
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        // Log reducido para mejor performance
        // System.out.println("Replicando a backups...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            // Usar ThreadPool en vez de crear nuevo Thread cada vez
            replicaPool.execute(() -> {
                try {
                    // System.out.println("Enviando a servidor " + info.getServerId() + "...");
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    // System.out.println("Replicado a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("Servidor " + info.getServerId() + " no disponible");
                }
            });
        }
    }

    // NUEVO: Para que EditorServiceImpl pueda buscar el líder
    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }

    // NUEVO: Método para cerrar el ThreadPool de manera ordenada (opcional)
    public void shutdown() {
        if (replicaPool != null && !replicaPool.isShutdown()) {
            replicaPool.shutdown();
            System.out.println("ThreadPool de réplicas cerrado");
        }
    }
}