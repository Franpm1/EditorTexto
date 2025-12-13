package server.infra;

import common.IEditorService;
import java.util.List;
import common.DocumentSnapshot;
import common.VectorClock;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        System.out.println("(Bully) Buscando servidores mayores...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    info.getStub().heartbeat();
                    foundHigher = true;
                    state.setCurrentLeaderId(info.getServerId());
                    System.out.println(" Servidor " + info.getServerId() + " responde.");
                } catch (Exception e) {}
            }
        }

        if (!foundHigher) {
            System.out.println("Nadie mayor responde. Preparando liderazgo.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
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
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("Consolidando estado del clúster...");
        String bestContent = null;
        VectorClock bestClock = null;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            try {
                DocumentSnapshot snapshot = info.getStub().getCurrentState();
                VectorClock remoteClock = snapshot.getClock();
                
                // USAMOS isNewer AQUÍ
                if (bestClock == null || remoteClock.isNewer(bestClock)) {
                    bestClock = remoteClock;
                    bestContent = snapshot.getContent();
                    System.out.println("   -> Mejor estado encontrado en nodo " + info.getServerId());
                }
            } catch (Exception e) {}
        }
        
        if (bestContent != null && bestClock != null) {
            try {
                myServiceStub.becomeLeader(bestContent, bestClock);
            } catch (Exception e) {
                System.out.println("Error aplicando estado: " + e.getMessage());
            }
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