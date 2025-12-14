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
        System.out.println("🔍 Buscando servidores con ID mayor...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();

        // Pool temporal para checks rápidos
        ExecutorService quickPool = Executors.newCachedThreadPool();
        
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
                    // Timeout MUY corto: 800ms
                    if (future.get(800, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        System.out.println("✓ Servidor " + info.getServerId() + " responde");
                        state.setCurrentLeaderId(info.getServerId());
                        break; // Con uno es suficiente
                    }
                } catch (TimeoutException e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " timeout");
                    future.cancel(true);
                } catch (Exception e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " error");
                }
            }
        }
        
        quickPool.shutdownNow();

        if (!foundHigher) {
            System.out.println("✅ Soy el líder.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Líder: " + state.getCurrentLeaderId());
        }
    }

    public void onLeaderDown() {
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("⚡ Iniciando elección...");
        boolean foundHigher = false;
        int myId = state.getMyServerId();
        
        ExecutorService quickPool = Executors.newCachedThreadPool();

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
                    if (future.get(500, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        System.out.println("✓ Nodo " + info.getServerId() + " responde");
                        break;
                    }
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        
        quickPool.shutdownNow();

        if (!foundHigher) {
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado...");
        
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
        
        // Esperar respuestas rápidas
        for (int i = 0; i < futures.size(); i++) {
            try {
                common.DocumentSnapshot snapshot = futures.get(i).get(800, TimeUnit.MILLISECONDS);
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
                // Timeout, continuar con siguiente
            }
        }
        
        syncPool.shutdownNow();
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
            } catch (Exception e) {
                // Continuar igual
            }
        }
    }

    private void becomeLeaderNow() {
        System.out.println("👑 LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        ExecutorService notifyPool = Executors.newCachedThreadPool();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            notifyPool.execute(() -> {
                try {
                    info.getStub().declareLeader(state.getMyServerId());
                } catch (Exception e) {
                    // Silencioso
                }
            });
        }
        
        notifyPool.shutdown();
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