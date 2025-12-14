package server.infra;

import common.IEditorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import server.core.ServerMain;

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
        System.out.println("🔍 Buscando servidores con ID mayor al mío (" + state.getMyServerId() + ") EN PARALELO...");
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        int myId = state.getMyServerId();

        // Crear todas las verificaciones en paralelo
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // SOLO UNA verificación (eliminado el heartbeat duplicado)
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }, ServerMain.GLOBAL_EXECUTOR);
                
                futures.add(future);
            }
        }

        // Esperar TODAS las respuestas con timeout
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            // Timeout agresivo: 1 segundo
            allFutures.get(1, TimeUnit.SECONDS);
            
            // Verificar si algún futuro retornó true
            boolean foundHigher = false;
            for (CompletableFuture<Boolean> future : futures) {
                if (future.getNow(false)) { // getNow no bloquea
                    foundHigher = true;
                    break;
                }
            }

            // Si NO encontré a nadie con ID mayor, soy el líder
            if (!foundHigher) {
                System.out.println("✅ No hay servidores con ID mayor respondiendo. Soy el líder.");
                syncStateBeforeBecomingLeader();
                becomeLeaderNow();
            } else {
                System.out.println("⏳ Hay servidores con ID mayor. Esperando líder...");
            }
        } catch (Exception e) {
            // Timeout - asumir que nadie responde y ser líder
            System.out.println("⏱️ Timeout en elección. Me proclamo líder.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    public void onLeaderDown() {
        // Prevenir elecciones duplicadas
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("⚡ Detectado fallo de líder. Iniciando elección PARALELA...");
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        info.getStub().heartbeat(); 
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }, ServerMain.GLOBAL_EXECUTOR);
                
                futures.add(future);
            }
        }

        // Verificar rápidamente
        boolean foundHigher = false;
        for (CompletableFuture<Boolean> future : futures) {
            if (future.getNow(false)) {
                foundHigher = true;
                break;
            }
        }

        if (!foundHigher) {
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado en paralelo antes de convertirme en líder...");
        
        List<CompletableFuture<common.DocumentSnapshot>> futures = new ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            CompletableFuture<common.DocumentSnapshot> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }, ServerMain.GLOBAL_EXECUTOR);
            
            futures.add(future);
        }
        
        // Buscar el estado más reciente entre todos los que respondan
        String latestContent = "";
        common.VectorClock latestClock = null;
        
        for (CompletableFuture<common.DocumentSnapshot> future : futures) {
            try {
                common.DocumentSnapshot snapshot = future.get(500, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    if (latestClock == null || isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                    }
                }
            } catch (Exception e) {
                // Ignorar timeout/error
            }
        }
        
        if (latestClock != null) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("✅ Estado sincronizado antes de ser líder.");
            } catch (Exception e) {
                System.out.println("⚠️ Error aplicando estado sincronizado: " + e.getMessage());
            }
        }
    }
    
    private boolean isClockNewer(common.VectorClock clock1, common.VectorClock clock2) {
        // Versión OPTIMIZADA sin parsear strings
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
        
        // Notificar a todos los backups EN PARALELO
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    info.getStub().declareLeader(state.getMyServerId());
                } catch (Exception e) {
                    // Error silencioso
                }
            }, ServerMain.GLOBAL_EXECUTOR));
        }
        
        System.out.println("✅ Notificaciones de liderazgo enviadas en paralelo.");
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