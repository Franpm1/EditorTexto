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

    public void startElectionOnStartup() {
        System.out.println("🔍 Buscando servidores con ID mayor al mío (" + state.getMyServerId() + ")...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        // Preguntar a TODOS los servidores con ID mayor
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    System.out.println("  Probando servidor " + info.getServerId() + "...");
                    info.getStub().heartbeat();
                    foundHigher = true;
                    System.out.println("  ✓ Servidor " + info.getServerId() + " responde");
                    
                    // Si responde, preguntarle quién es el líder actual
                    try {
                        info.getStub().heartbeat(); // Doble verificación
                        state.setCurrentLeaderId(info.getServerId());
                        System.out.println("  Líder actual: " + info.getServerId());
                    } catch (Exception e) {
                        // No pasa nada, seguimos buscando
                    }
                } catch (Exception e) {
                    System.out.println("  ✗ Servidor " + info.getServerId() + " no disponible");
                }
            }
        }

        // Si NO encontré a nadie con ID mayor, soy el líder
        if (!foundHigher) {
            System.out.println("✅ No hay servidores con ID mayor. Soy el líder.");
            
            // NUEVO: Sincronizar estado antes de proclamarme líder
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Esperando notificación del líder...");
        }
    }

    public void onLeaderDown() {
        // Prevenir elecciones duplicadas
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("⚡ Detectado fallo de líder. Iniciando elección...");
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
            // NUEVO: Sincronizar estado antes de proclamarme líder
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    // NUEVO: Método para sincronizar estado antes de convertirse en líder
    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado antes de convertirme en líder...");
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        boolean gotState = false;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            try {
                common.DocumentSnapshot snapshot = info.getStub().getCurrentState();
                System.out.println("Estado obtenido del servidor " + info.getServerId() + 
                    ": VC=" + snapshot.getClock());
                
                if (!gotState) {
                    latestContent = snapshot.getContent();
                    latestClock = snapshot.getClock();
                    gotState = true;
                } else {
                    // Comparar vector clocks para quedarse con el más reciente
                    if (isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                    }
                }
            } catch (Exception e) {
                System.out.println("No se pudo obtener estado del servidor " + info.getServerId());
            }
        }
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("Estado sincronizado: " + 
                    (latestContent.isEmpty() ? "(vacío)" : latestContent.substring(0, Math.min(50, latestContent.length()))));
            } catch (Exception e) {
                System.out.println("Error aplicando estado sincronizado: " + e.getMessage());
            }
        }
    }
    
    // NUEVO: Método para comparar vector clocks
    private boolean isClockNewer(common.VectorClock clock1, common.VectorClock clock2) {
        String s1 = clock1.toString().replaceAll("\\[|\\]", "");
        String s2 = clock2.toString().replaceAll("\\[|\\]", "");
        String[] parts1 = s1.split(",");
        String[] parts2 = s2.split(",");
        
        boolean atLeastOneGreater = false;
        boolean atLeastOneLess = false;
        
        int minLength = Math.min(parts1.length, parts2.length);
        
        for (int i = 0; i < minLength; i++) {
            int v1 = Integer.parseInt(parts1[i].trim());
            int v2 = Integer.parseInt(parts2[i].trim());
            
            if (v1 > v2) atLeastOneGreater = true;
            if (v1 < v2) atLeastOneLess = true;
        }
        
        return atLeastOneGreater && !atLeastOneLess;
    }

    private void becomeLeaderNow() {
        System.out.println("👑 Me proclamo LÍDER (ID " + state.getMyServerId() + ")");
        
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