package server.infra;

import common.IEditorService;
import common.VectorClockComparator;
import java.util.List;
import java.util.concurrent.*;

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
        System.out.println("🔍 BULLY: Iniciando elección al arrancar (ID: " + state.getMyServerId() + ")...");
        
        // Pequeña pausa para estabilización
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        int myId = state.getMyServerId();
        boolean foundHigher = false;
        int highestRespondingId = -1;
        
        ExecutorService quickPool = Executors.newCachedThreadPool();
        
        // BULLY: Preguntar a TODOS los servidores con ID mayor
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                Future<Boolean> future = quickPool.submit(() -> {
                    try {
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(1500, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        highestRespondingId = Math.max(highestRespondingId, info.getServerId());
                        System.out.println("✓ BULLY: Servidor " + info.getServerId() + " responde (ID mayor)");
                    }
                } catch (TimeoutException e) {
                    System.out.println("✗ BULLY: Servidor " + info.getServerId() + " timeout");
                    future.cancel(true);
                } catch (Exception e) {
                    System.out.println("✗ BULLY: Servidor " + info.getServerId() + " error");
                }
            }
        }
        
        quickPool.shutdownNow();
        
        // BULLY: Si hay servidores con ID mayor activos, reconocer al de ID más alto como líder
        if (foundHigher && highestRespondingId != -1) {
            System.out.println("⏳ BULLY: Reconociendo a servidor " + highestRespondingId + " como líder (ID mayor)");
            state.setCurrentLeaderId(highestRespondingId);
            state.setLeader(false);
        } 
        // BULLY: Si NO hay servidores con ID mayor activos, yo soy el líder
        else {
            System.out.println("✅ BULLY: Soy el servidor con ID más alto activo");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    public void onLeaderDown() {
        if (state.isLeader()) {
            return;
        }
        
        System.out.println("⚡ BULLY: Líder posiblemente caído, iniciando elección...");
        
        // Primero verificar si el líder realmente cayó
        int currentLeader = state.getCurrentLeaderId();
        if (currentLeader != -1) {
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() == currentLeader) {
                    try {
                        info.getStub().heartbeat();
                        System.out.println("✓ BULLY: El líder " + currentLeader + " SÍ responde");
                        return;
                    } catch (Exception e) {
                        System.out.println("✗ BULLY: Confirmado - líder " + currentLeader + " NO responde");
                        break;
                    }
                }
            }
        }
        
        // Líder confirmado caído
        state.setCurrentLeaderId(-1);
        System.out.println("🔄 BULLY: Iniciando nueva elección...");
        
        startElectionOnStartup(); // Reutilizar la lógica de elección
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 BULLY: Sincronizando estado antes de ser líder...");
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        boolean gotState = false;
        
        ExecutorService syncPool = Executors.newCachedThreadPool();
        List<Future<common.DocumentSnapshot>> futures = new java.util.ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(syncPool.submit(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                common.DocumentSnapshot snapshot = futures.get(i).get(2000, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    if (!gotState) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        gotState = true;
                    } else if (VectorClockComparator.isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                    }
                }
            } catch (Exception e) {
                // Timeout, continuar
            }
        }
        
        syncPool.shutdownNow();
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
            } catch (Exception e) {
                System.out.println("⚠️ BULLY: Error al sincronizar estado");
            }
        }
    }

    private void becomeLeaderNow() {
        System.out.println("👑 BULLY: DECLARÁNDOME LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        // BULLY: Notificar a TODOS los servidores, especialmente a los que eran líderes
        System.out.println("📢 BULLY: Notificando a todos los servidores...");
        
        ExecutorService notifyPool = Executors.newFixedThreadPool(3);
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            notifyPool.execute(() -> {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        if (attempt > 0) {
                            System.out.println("BULLY: Reintentando notificar a servidor " + info.getServerId());
                            Thread.sleep(300);
                        }
                        
                        info.getStub().declareLeader(state.getMyServerId());
                        System.out.println("✓ BULLY: Servidor " + info.getServerId() + " notificado");
                        break;
                    } catch (Exception e) {
                        if (attempt == 2) {
                            System.out.println("✗ BULLY: No se pudo notificar a servidor " + info.getServerId());
                        }
                    }
                }
            });
            
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        
        notifyPool.shutdown();
        try {
            notifyPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            notifyPool.shutdownNow();
        }
        
        System.out.println("🎯 BULLY: Liderazgo establecido correctamente");
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