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

    // NUEVO: Elección activa al iniciar el servidor
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
            becomeLeaderNow();
        }
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