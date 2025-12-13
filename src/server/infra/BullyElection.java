package server.infra;

import common.IEditorService;
import java.util.List;

public class BullyElection {

    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;

    public BullyElection(ServerState state,
                         List<RemoteServerInfo> allServers,
                         IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    /**
     * Llamado por HeartbeatMonitor cuando el líder no responde.
     */
    public synchronized void onLeaderDown() {
        // Si ya soy líder, no hago nada
        if (state.isLeader()) {
            return;
        }

        System.out.println("[BULLY] Líder no responde. Iniciando elección...");
        int myId = state.getMyServerId();
        boolean higherAlive = false;

        // Paso 1: comprobar si hay algún servidor con ID mayor vivo
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    info.getStub().heartbeat();
                    System.out.println("[BULLY] Servidor " + info.getServerId() + " responde. Espero.");
                    higherAlive = true;
                } catch (Exception e) {
                    // No responde → ignorar
                }
            }
        }

        // Paso 2: si nadie mayor responde, me proclamo líder
        if (!higherAlive) {
            becomeLeaderNow();
        }
    }

    private void becomeLeaderNow() {
        int myId = state.getMyServerId();
        System.out.println("[BULLY] Me proclamo LÍDER (ID " + myId + ")");

        // Actualizar estado local
        state.setLeader(true);
        state.setCurrentLeaderId(myId);

        // Notificar al resto de servidores
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == myId) continue;
            try {
                info.getStub().declareLeader(myId);
                System.out.println("[BULLY] Notificado servidor " + info.getServerId());
            } catch (Exception e) {
                System.out.println("[BULLY] No se pudo notificar a servidor " + info.getServerId());
            }
        }

        System.out.println("[BULLY] Elección completada. Ahora acepto escrituras.");
    }

    /**
     * Utilidad para otros componentes (p. ej. redirección).
     */
    public RemoteServerInfo getCurrentLeaderInfo() {
        int leaderId = state.getCurrentLeaderId();
        if (leaderId == -1) return null;

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == leaderId) {
                return info;
            }
        }
        return null;
    }
}
