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
    private volatile boolean electionInProgress = false;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        if (electionInProgress) return;
        electionInProgress = true;
        
        System.out.println("🔍 INICIANDO ELECCIÓN: Buscando servidores con ID > " + state.getMyServerId());
        
        List<CompletableFuture<RemoteServerInfo>> higherAliveFutures = new ArrayList<>();
        int myId = state.getMyServerId();

        // 1. Buscar TODOS los servidores con ID mayor
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                CompletableFuture<RemoteServerInfo> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Timeout MUY CORTO: 300ms
                        // Si responde en 300ms, está VIVO
                        var stub = info.getStub();
                        stub.heartbeat();
                        return info; // Este servidor está VIVO
                    } catch (Exception e) {
                        return null; // Este servidor NO responde
                    }
                }, ServerMain.GLOBAL_EXECUTOR);
                
                higherAliveFutures.add(future);
            }
        }

        if (higherAliveFutures.isEmpty()) {
            // NO HAY servidores con ID mayor -> soy líder
            System.out.println("✅ NO HAY servidores con ID mayor. Soy líder.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
            electionInProgress = false;
            return;
        }

        // 2. Esperar respuestas (timeout: 1 segundo)
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            higherAliveFutures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Timeout - algunos no respondieron
        }
        
        // 3. Verificar: ¿Algún servidor con ID mayor está VIVO?
        boolean foundHigherAlive = false;
        int highestAliveId = -1;
        
        for (CompletableFuture<RemoteServerInfo> future : higherAliveFutures) {
            try {
                RemoteServerInfo aliveServer = future.getNow(null);
                if (aliveServer != null) {
                    foundHigherAlive = true;
                    if (aliveServer.getServerId() > highestAliveId) {
                        highestAliveId = aliveServer.getServerId();
                    }
                    System.out.println("   ✓ Servidor " + aliveServer.getServerId() + " RESPONDE");
                }
            } catch (Exception e) {
                // Ignorar
            }
        }
        
        // 4. Decisión CRÍTICA
        if (foundHigherAlive) {
            // HAY servidores con ID mayor vivos -> NO soy líder
            System.out.println("⏳ Hay servidores con ID mayor vivos. El líder debería ser: " + highestAliveId);
            System.out.println("   Esperando que el servidor " + highestAliveId + " se declare líder...");
            
            // IMPORTANTE: Esperar un tiempo a que el de mayor ID se declare líder
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000); // Esperar 2 segundos
                    
                    // Si después de 2 segundos nadie se ha declarado líder...
                    if (state.getCurrentLeaderId() == -1) {
                        System.out.println("⚠️  Nadie se ha declarado líder. Reintentando elección...");
                        electionInProgress = false;
                        startElectionOnStartup();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            // NINGÚN servidor con ID mayor responde -> soy líder
            System.out.println("✅ NINGÚN servidor con ID mayor responde. Me proclamo líder.");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        }
        
        electionInProgress = false;
    }

    public void onLeaderDown() {
        // Prevenir múltiples elecciones simultáneas
        if (electionInProgress || state.isLeader()) {
            return;
        }
        
        System.out.println("⚡ LÍDER CAÍDO. Iniciando nueva elección...");
        startElectionOnStartup();
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado con otros servidores...");
        
        // Solo sincronizar con servidores que estén vivos
        List<CompletableFuture<common.DocumentSnapshot>> snapshots = new ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            snapshots.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }, ServerMain.GLOBAL_EXECUTOR));
        }
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        
        // Esperar un tiempo corto por respuestas
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        for (CompletableFuture<common.DocumentSnapshot> future : snapshots) {
            try {
                common.DocumentSnapshot snapshot = future.getNow(null);
                if (snapshot != null) {
                    if (latestClock == null || snapshot.getClock().isNewerThan(latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                    }
                }
            } catch (Exception e) {
                // Ignorar
            }
        }
        
        if (latestClock != null) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("✅ Estado sincronizado desde otro servidor.");
            } catch (Exception e) {
                System.out.println("⚠️  Error al sincronizar estado: " + e.getMessage());
            }
        }
    }

    private void becomeLeaderNow() {
        System.out.println("👑 ========== SOY EL NUEVO LÍDER (ID " + state.getMyServerId() + ") ==========");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        // Notificar a TODOS los servidores EN PARALELO
        List<CompletableFuture<Void>> notifications = new ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            notifications.add(CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("   Notificando a servidor " + info.getServerId() + "...");
                    info.getStub().declareLeader(state.getMyServerId());
                    System.out.println("   ✓ Servidor " + info.getServerId() + " notificado");
                } catch (Exception e) {
                    System.out.println("   ✗ Servidor " + info.getServerId() + " no disponible");
                }
            }, ServerMain.GLOBAL_EXECUTOR));
        }
        
        // No esperar a que terminen todas las notificaciones
        System.out.println("✅ Notificaciones de liderazgo enviadas.");
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