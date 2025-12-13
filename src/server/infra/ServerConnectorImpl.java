package server.infra;

import common.VectorClock;
import java.util.List;

public class ServerConnectorImpl implements IServerConnector {
    private final List<RemoteServerInfo> backupServers;
    private final int myId;

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> backupServers) {
        this.myId = myId;
        this.backupServers = backupServers;
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        System.out.println("Replicando a backups...");
        
        for (RemoteServerInfo info : backupServers) {
            if (info.getServerId() == myId) continue;

            new Thread(() -> {
                try {
                    System.out.println("Enviando a servidor " + info.getServerId() + "...");
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    System.out.println("Replicado a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("Servidor " + info.getServerId() + " no disponible");
                }
            }).start();
        }
    }

    // NUEVO: Para que EditorServiceImpl pueda buscar el líder
    public List<RemoteServerInfo> getOtherServers() { return backupServers; }

}