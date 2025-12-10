package server;

import common.IEditorService;
import common.VectorClock;

import java.util.List;

/**
 * Clase usada por Pareja B para replicar el documento
 * a los servidores backup sin preocuparse del detalle de RMI.
 */
public class ServerConnectorImpl {

    private final List<RemoteServerInfo> backupServers;

    public ServerConnectorImpl(List<RemoteServerInfo> backupServers) {
        this.backupServers = backupServers;
    }

    /**
     * El líder llama a esto DESPUÉS de aplicar una operación.
     */
    public void propagateToBackups(String fullDocument,
                                   VectorClock clockSnapshot) {
        for (RemoteServerInfo info : backupServers) {
            try {
                IEditorService stub = info.getStub();
                stub.applyReplication(fullDocument, clockSnapshot);
            } catch (Exception e) {
                System.out.println("[Replication] Backup " + info.getServerId() +
                        " no responde. Se ignora (fire-and-forget).");
            }
        }
    }
}
