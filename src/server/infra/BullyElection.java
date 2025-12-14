package server.infra;

import common.IEditorService;
import common.DocumentSnapshot;
import common.VectorClock;  // <-- ¡ESTE ES EL IMPORT QUE FALTA!
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

    public void startElectionOnStartup() {
        System.out.println("Buscando servidores con ID mayor al mío (" + state.getMyServerId() + ")...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    System.out.println("  Probando servidor " + info.getServerId() + "...");
                    info.getStub().heartbeat();
                    foundHigher = true;
                    System.out.println("  ✓ Servidor " + info.getServerId() + " responde");
                    
                    try {
                        info.getStub().heartbeat();
                        state.setCurrentLeaderId(info.getServerId());
                        System.out.println("  Líder actual: " + info.getServerId());
                    } catch (Exception e) {
                        // No pasa nada
                    }
                } catch (Exception e) {
                    System.out.println("  ✗ Servidor " + info.getServerId() + " no disponible");
                }
            }
        }

        if (!foundHigher) {
            System.out.println("✅ No hay servidores con ID mayor. Soy el líder.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Esperando notificación del líder...");
        }
    }

    public void onLeaderDown() {
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("Detectado fallo de líder. Iniciando elección...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    info.getStub().heartbeat(); 
                    foundHigher = true;
                    System.out.println("✓ Nodo " + info.getServerId() + " responde");
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
        System.out.println("Sincronizando estado antes de convertirme en líder...");
        
        String latestContent = "";
        VectorClock latestClock = null;
        boolean gotState = false;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            try {
                DocumentSnapshot snapshot = info.getStub().getCurrentState();
                System.out.println("Estado obtenido del servidor " + info.getServerId() + 
                    ": VC=" + snapshot.getClock());
                
                if (!gotState) {
                    latestContent = snapshot.getContent();
                    latestClock = snapshot.getClock();
                    gotState = true;
                } else {
                    // Simple: quedarse con el último estado obtenido
                    latestContent = snapshot.getContent();
                    latestClock = snapshot.getClock();
                }
            } catch (Exception e) {
                System.out.println("No se pudo obtener estado del servidor " + info.getServerId() + ": " + e.getMessage());
            }
        }
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("Estado sincronizado desde otro servidor");
            } catch (Exception e) {
                System.out.println("Error aplicando estado sincronizado: " + e.getMessage());
            }
        } else {
            System.out.println("No se pudo obtener estado de otros servidores, inicio con documento vacío");
        }
    }

    private void becomeLeaderNow() {
        System.out.println("Me proclamo LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId()); 
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            try {
                info.getStub().declareLeader(state.getMyServerId());
                System.out.println("✓ Notificado a servidor " + info.getServerId());
            } catch (Exception e) {
                System.out.println("✗ No se pudo notificar a servidor " + info.getServerId());
            }
        }
        System.out.println("✅ Ahora acepto escrituras como líder");
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