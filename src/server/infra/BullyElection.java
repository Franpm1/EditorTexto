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
    private long lastElectionTime = 0;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        if (electionInProgress) return;
        
        // Prevenir elecciones demasiado frecuentes
        long now = System.currentTimeMillis();
        if (now - lastElectionTime < 3000) { // Mínimo 3 segundos entre elecciones
            System.out.println("⏳ Elección reciente, esperando...");
            return;
        }
        
        electionInProgress = true;
        lastElectionTime = now;
        
        System.out.println("🔍 INICIANDO ELECCIÓN: Buscando servidores con ID > " + state.getMyServerId());
        
        List<CompletableFuture<RemoteServerInfo>> higherAliveFutures = new ArrayList<>();
        int myId = state.getMyServerId();

        // 1. Buscar TODOS los servidores con ID mayor
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                CompletableFuture<RemoteServerInfo> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Timeout: 500ms
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

        // 2. Esperar respuestas (timeout: 1.5 segundos)
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            higherAliveFutures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(1500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Timeout - algunos no respondieron
        }
        
        // 3. Verificar: ¿Algún servidor con ID mayor está VIVO?
        boolean foundHigherAlive = false;
        int highestAliveId = -1;
        RemoteServerInfo highestAliveServer = null;
        
        for (CompletableFuture<RemoteServerInfo> future : higherAliveFutures) {
            try {
                RemoteServerInfo aliveServer = future.getNow(null);
                if (aliveServer != null) {
                    foundHigherAlive = true;
                    if (aliveServer.getServerId() > highestAliveId) {
                        highestAliveId = aliveServer.getServerId();
                        highestAliveServer = aliveServer;
                    }
                    System.out.println("   ✓ Servidor " + aliveServer.getServerId() + " RESPONDE");
                }
            } catch (Exception e) {
                // Ignorar
            }
        }
        
        // 4. Decisión CORREGIDA
        if (foundHigherAlive) {
            // HAY servidores con ID mayor vivos -> NO soy líder
            System.out.println("⏳ Hay servidores con ID mayor vivos. Líder actual debería ser: " + highestAliveId);
            
            // ***** CAMBIO CRÍTICO AQUÍ *****
            // En vez de esperar a que se declare, PREGUNTAR DIRECTAMENTE al de mayor ID
            
            if (highestAliveServer != null) {
                System.out.println("   Preguntando al servidor " + highestAliveId + " si es líder...");
                
                try {
                    // Intentar obtener su estado para ver si ya es líder
                    common.DocumentSnapshot snapshot = highestAliveServer.getStub().getCurrentState();
                    System.out.println("   Servidor " + highestAliveId + " está activo y responde.");
                    
                    // Si responde correctamente, asumir que ES o SERÁ el líder
                    // Actualizar nuestro estado para saber quién es el líder
                    state.setCurrentLeaderId(highestAliveId);
                    state.setLeader(false);
                    
                    System.out.println("   ✅ Líder establecido: servidor " + highestAliveId);
                    
                } catch (Exception e) {
                    System.out.println("   ❌ No se pudo contactar con servidor " + highestAliveId);
                    // Si el de mayor ID no responde a getCurrentState, quizás tengo que ser líder
                    System.out.println("   Intentando convertirme en líder de respaldo...");
                    syncStateBeforeBecomingLeader();
                    becomeLeaderNow();
                }
            }
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
        
        System.out.println("⚡ DETECTADO: Posible caída de líder. Iniciando verificación...");
        
        // ANTES de iniciar elección completa, verificar si el líder actual responde
        int currentLeaderId = state.getCurrentLeaderId();
        if (currentLeaderId != -1 && currentLeaderId != state.getMyServerId()) {
            RemoteServerInfo currentLeader = null;
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() == currentLeaderId) {
                    currentLeader = info;
                    break;
                }
            }
            
            if (currentLeader != null) {
                try {
                    // Última verificación rápida
                    currentLeader.getStub().heartbeat();
                    System.out.println("✅ Líder actual " + currentLeaderId + " SÍ responde. Cancelando elección.");
                    return; // ¡El líder SÍ está vivo! No iniciar elección
                } catch (Exception e) {
                    System.out.println("❌ Confirmado: líder " + currentLeaderId + " NO responde.");
                    // Continuar con la elección...
                }
            }
        }
        
        System.out.println("🚨 INICIANDO ELECCIÓN por fallo de líder...");
        startElectionOnStartup();
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado con otros servidores...");
        
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
            Thread.sleep(800); // 800ms para sincronización
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
                        System.out.println("   Estado obtenido de servidor con VC: " + latestClock);
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
        } else {
            System.out.println("⚠️  No se pudo obtener estado de otros servidores. Continuando con estado local.");
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