package server.infra;

import common.DocumentSnapshot;
import common.IEditorService;
import common.VectorClock;
import java.util.List;
import java.util.concurrent.*;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        System.out.println("[ELECCIÓN RÁPIDA] Buscando servidores con ID mayor...");
        
        boolean foundHigher = quickCheckForHigherServers(1500);
        
        if (!foundHigher) {
            System.out.println("✅ No hay servidores con ID mayor. Sincronizando y convirtiéndome en líder...");
            executor.submit(this::quickSyncAndBecomeLeader);
        } else {
            System.out.println("⏳ Hay servidores mayores, esperando líder...");
        }
    }

    public void onLeaderDown() {
        if (state.isLeader() || state.getCurrentLeaderId() != -1) {
            return;
        }
        
        System.out.println("[ELECCIÓN RÁPIDA] Líder caído, buscando reemplazo...");
        
        boolean foundHigher = quickCheckForHigherServers(1000);
        
        if (!foundHigher) {
            System.out.println("🔄 Sincronizando rápidamente y tomando liderazgo...");
            executor.submit(this::quickSyncAndBecomeLeader);
        }
    }

    private boolean quickCheckForHigherServers(int timeoutMs) {
        List<CompletableFuture<Boolean>> futures = new CopyOnWriteArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > state.getMyServerId()) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return pingServerWithTimeout(info, 500);
                    } catch (Exception e) {
                        return false;
                    }
                }, executor));
            }
        }
        
        if (futures.isEmpty()) return false;
        
        CompletableFuture<Object> anyResult = CompletableFuture.anyOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            Object result = anyResult.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(result)) {
                return true;
            }
        } catch (TimeoutException e) {
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            // Ignorar
        }
        
        return false;
    }

    private boolean pingServerWithTimeout(RemoteServerInfo info, int timeoutMs) {
        try {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    info.getStub().heartbeat();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private void quickSyncAndBecomeLeader() {
        System.out.println("[SINCRONIZACIÓN RÁPIDA] Obteniendo estado más reciente...");
        
        DocumentSnapshot latestSnapshot = getLatestStateQuickly(2000);
        
        try {
            if (latestSnapshot != null) {
                myServiceStub.becomeLeader(
                    latestSnapshot.getContent(), 
                    latestSnapshot.getClock()
                );
                System.out.println("✅ Estado sincronizado desde otro servidor");
            } else {
                // Si no hay estado disponible, empezar vacío
                myServiceStub.becomeLeader(
                    "", 
                    new VectorClock(allServers.size())
                );
                System.out.println("📄 Iniciando con documento vacío");
            }
            
            // CRÍTICO: Notificar liderazgo EN PARALELO
            notifyLeadershipInBackground();
            
        } catch (Exception e) {
            System.err.println("❌ Error en sincronización: " + e.getMessage());
            // Fallback: intentar sin sincronización
            try {
                myServiceStub.becomeLeader("", new VectorClock(allServers.size()));
                notifyLeadershipInBackground();
            } catch (Exception ex) {
                System.err.println("❌ Fallback también falló: " + ex.getMessage());
            }
        }
    }

    private DocumentSnapshot getLatestStateQuickly(int timeoutMs) {
        List<CompletableFuture<DocumentSnapshot>> futures = new CopyOnWriteArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return getServerStateWithTimeout(info, 1000);
                } catch (Exception e) {
                    return null;
                }
            }, executor));
        }
        
        if (futures.isEmpty()) return null;
        
        CompletableFuture<Object> firstSuccess = CompletableFuture.anyOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            Object result = firstSuccess.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result instanceof DocumentSnapshot) {
                return (DocumentSnapshot) result;
            }
        } catch (TimeoutException e) {
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            // Ignorar
        }
        
        return null;
    }

    private DocumentSnapshot getServerStateWithTimeout(RemoteServerInfo info, int timeoutMs) {
        try {
            CompletableFuture<DocumentSnapshot> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }, executor);
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyLeadershipInBackground() {
        System.out.println("👑 Me proclamo LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        // CRÍTICO: Notificar a TODOS en paralelo
        List<CompletableFuture<Void>> notifications = new CopyOnWriteArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            notifications.add(CompletableFuture.runAsync(() -> {
                try {
                    info.getStub().declareLeader(state.getMyServerId());
                    System.out.println("  ✓ Notificado a servidor " + info.getServerId());
                } catch (Exception e) {
                    // Intentar una vez más
                    try {
                        Thread.sleep(100);
                        info.getStub().declareLeader(state.getMyServerId());
                    } catch (Exception ex) {
                        System.out.println("  ✗ Servidor " + info.getServerId() + " no disponible");
                    }
                }
            }, executor));
        }
        
        // Esperar máximo 3 segundos para notificaciones
        try {
            CompletableFuture.allOf(
                notifications.toArray(new CompletableFuture[0])
            ).get(3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Algunas notificaciones fallaron, pero continuamos
        }
        
        System.out.println("🚀 Líder completamente operativo - clientes deberían ver estado actual");
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