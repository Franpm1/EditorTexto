package server.infra;

import common.VectorClock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class ServerConnectorImpl implements IServerConnector {
    private final List<RemoteServerInfo> backupServers;
    private final int myId;

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> backupServers) {
        this.myId = myId;
        this.backupServers = backupServers;
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        System.out.println("Replicando a " + (backupServers.size() - 1) + " backups EN PARALELO...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            // Usar CompletableFuture para ejecución paralela NO BLOQUEANTE
            CompletableFuture.runAsync(() -> {
                try {
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    // Log reducido para velocidad
                } catch (Exception e) {
                    // Error silencioso - no imprimir (evita bloqueo I/O)
                }
            }, ServerMain.GLOBAL_EXECUTOR); // Usar thread pool global
        }
        // NO ESPERAR a que terminen - continuar inmediatamente
    }

    public List<RemoteServerInfo> getAllServers() {
        return backupServers;
    }
}