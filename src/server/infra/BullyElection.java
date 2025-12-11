package server.infra;

import common.IEditorService;
import java.util.List;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void onLeaderDown() {
        // FIX: Si ya soy líder o hay líder conocido, no hacer nada
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("Detectado fallo de líder. Iniciando eleccion...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    info.getStub().heartbeat(); 
                    foundHigher = true;
                    System.out.println("Nodo " + info.getServerId() + " responde");
                } catch (Exception e) {
                    // Nodo no disponible
                }
            }
        }

        if (!foundHigher) {
            becomeLeaderNow();
        }
    }

    private void becomeLeaderNow() {
        System.out.println("Me proclamo LIDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId()); 
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            try {
                info.getStub().declareLeader(state.getMyServerId());
                System.out.println("Notificado a servidor " + info.getServerId());
            } catch (Exception e) {
                System.out.println("No se pudo notificar a servidor " + info.getServerId());
            }
        }
        System.out.println("Ahora acepto escrituras como lider");
    }

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