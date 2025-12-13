package server.infra;

import common.VectorClock;
import java.util.List;

public class ServerConnectorImpl implements IServerConnector {

    // En main se pasa allServers (no solo backups), así que lo llamamos claramente.
    private final List<RemoteServerInfo> otherServers; 
    private final int myId;

    public ServerConnectorImpl(int myId, List<RemoteServerInfo> allServers) {
        this.myId = myId;
        this.otherServers = allServers;
    }

    @Override
    public void propagateToBackups(String fullDocument, VectorClock clockSnapshot) {
        System.out.println("Replicando a backups...");

        for (RemoteServerInfo info : otherServers) {
            if (info.getServerId() == myId) continue;

            new Thread(() -> {
                try {
                    System.out.println("Enviando a servidor " + info.getServerId() + "...");
                    info.getStub().applyReplication(fullDocument, clockSnapshot);
                    System.out.println("Replicado a servidor " + info.getServerId());
                } catch (Exception e) {
                    System.out.println("Servidor " + info.getServerId() + " no disponible");
                }
            }, "replicate-to-" + info.getServerId()).start();
        }
    }

    /**
     * Para que EditorServiceImpl pueda buscar el líder.
     * Devuelve la lista de servidores conocidos (típicamente allServers).
     */
    public List<RemoteServerInfo> getAllServers() {
        return otherServers;
    }
}
