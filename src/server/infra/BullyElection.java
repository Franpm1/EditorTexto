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
    private static final long ELECTION_COOLDOWN_MS = 3000;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }
    
    // **NUEVO MÉTODO:** Para que HeartbeatMonitor pueda acceder a la lista
    public List<RemoteServerInfo> getAllServers() {
        return new ArrayList<>(allServers);
    }

    public void startElectionOnStartup() {
        if (electionInProgress) return;
        
        // Prevenir elecciones demasiado frecuentes
        long now = System.currentTimeMillis();
        if (now - lastElectionTime < ELECTION_COOLDOWN_MS) {
            System.out.println("⏳ Elección reciente (" + (now - lastElectionTime) + "ms), esperando...");
            return;
        }
        
        electionInProgress = true;
        lastElectionTime = now;
        state.resetElectionTrigger();
        
        System.out.println("\n🎯 ===== INICIANDO ELECCIÓN BULLY =====");
        System.out.println("Mi ID: " + state.getMyServerId());
        
        // 1. Enviar mensaje de ELECCIÓN a todos con ID mayor
        List<CompletableFuture<Boolean>> higherResponses = new ArrayList<>();
        int myId = state.getMyServerId();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                System.out.println("  Enviando ELECTION a servidor " + info.getServerId());
                
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // **CORRECCIÓN:** Enviar heartbeat primero para ver si está vivo
                        info.getStub().heartbeat();
                        
                        // Si responde, significa que está vivo y con ID mayor
                        // Debería iniciar SU PROPIA elección
                        System.out.println("    ✓ Servidor " + info.getServerId() + " RESPONDE (ID mayor vivo)");
                        
                        // Esperar un poco a que inicie su elección
                        Thread.sleep(800);
                        
                        // Verificar si se ha declarado líder
                        if (state.getCurrentLeaderId() != -1 && state.getCurrentLeaderId() != myId) {
                            System.out.println("    ⏱️  Otro líder ya se declaró: " + state.getCurrentLeaderId());
                            return true; // Hay respuesta de servidor mayor
                        }
                        
                        return true; // Hay servidor mayor vivo
                    } catch (Exception e) {
                        System.out.println("    ✗ Servidor " + info.getServerId() + " NO RESPONDE");
                        return false; // Servidor no responde
                    }
                }, ServerMain.GLOBAL_EXECUTOR);
                
                higherResponses.add(future);
            }
        }

        // 2. Esperar respuestas con timeout
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            higherResponses.toArray(new CompletableFuture[0])
        );
        
        boolean higherAliveExists = false;
        
        try {
            // Timeout más corto para decisión rápida
            allFutures.get(1200, TimeUnit.MILLISECONDS);
            
            // Verificar resultados
            for (CompletableFuture<Boolean> future : higherResponses) {
                try {
                    if (future.getNow(false)) {
                        higherAliveExists = true;
                        break;
                    }
                } catch (Exception e) {
                    // Ignorar
                }
            }
        } catch (Exception e) {
            // Timeout - verificar lo que tenemos hasta ahora
            for (CompletableFuture<Boolean> future : higherResponses) {
                try {
                    if (future.getNow(false)) {
                        higherAliveExists = true;
                        break;
                    }
                } catch (Exception ex) {
                    // Ignorar
                }
            }
        }

        // 3. DECISIÓN FINAL
        if (higherAliveExists) {
            // Hay servidores con ID mayor vivos - esperar que uno se declare líder
            System.out.println("⏳ Hay servidores con ID mayor vivos. Esperando declaración de líder...");
            
            // Esperar 2 segundos a que se declare un líder
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000);
                    
                    // Verificar si alguien se declaró líder
                    if (state.getCurrentLeaderId() == -1 || state.getCurrentLeaderId() == myId) {
                        System.out.println("⏰ Timeout: Nadie se declaró líder. Yo tomo el liderazgo.");
                        becomeLeaderNow();
                    } else {
                        System.out.println("✅ Líder establecido: servidor " + state.getCurrentLeaderId());
                    }
                    
                    electionInProgress = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    electionInProgress = false;
                }
            }, ServerMain.GLOBAL_EXECUTOR);
            
        } else {
            // NO HAY servidores con ID mayor vivos -> YO soy líder
            System.out.println("✅ NO HAY servidores con ID mayor vivos. YO soy el líder.");
            
            // Pequeña espera aleatoria para evitar colisiones
            try {
                int randomWait = 200 + (int)(Math.random() * 800); // 200-1000ms
                Thread.sleep(randomWait);
                
                // Doble verificación por si acaso
                if (!checkForHigherAliveServers()) {
                    syncStateBeforeBecomingLeader();
                    becomeLeaderNow();
                } else {
                    System.out.println("⚠️  ¡Encontrado servidor mayor durante espera! Cancelando.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            electionInProgress = false;
        }
    }
    
    private boolean checkForHigherAliveServers() {
        int myId = state.getMyServerId();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                try {
                    info.getStub().heartbeat();
                    System.out.println("   ! Servidor " + info.getServerId() + " está vivo después de todo");
                    return true;
                } catch (Exception e) {
                    // Continúa
                }
            }
        }
        return false;
    }

    public void onLeaderDown() {
        // Prevenir múltiples elecciones simultáneas
        if (electionInProgress || state.isLeader()) {
            return;
        }
        
        System.out.println("⚡ DETECTADO: Posible caída de líder " + state.getCurrentLeaderId());
        
        // Pequeña espera aleatoria para evitar storm de elecciones
        try {
            int randomWait = 300 + (int)(Math.random() * 700); // 300-1000ms
            Thread.sleep(randomWait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Verificar una última vez si el líder responde
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
                    currentLeader.getStub().heartbeat();
                    System.out.println("✅ Líder actual " + currentLeaderId + " SÍ responde. Cancelando elección.");
                    return;
                } catch (Exception e) {
                    System.out.println("❌ Confirmado: líder " + currentLeaderId + " NO responde.");
                }
            }
        }
        
        // Limpiar estado de líder
        state.setCurrentLeaderId(-1);
        
        System.out.println("🚨 INICIANDO ELECCIÓN por fallo de líder...");
        startElectionOnStartup();
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado con otros servidores...");
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        int serverCount = 0;
        
        // Obtener estado del mayor número de servidores posible
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            try {
                common.DocumentSnapshot snapshot = info.getStub().getCurrentState();
                if (snapshot != null) {
                    serverCount++;
                    
                    if (latestClock == null || 
                        snapshot.getClock().isNewerThan(latestClock) ||
                        (snapshot.getClock().toString().equals(latestClock.toString()) && 
                         snapshot.getContent().length() > latestContent.length())) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                    }
                    System.out.println("   Estado de servidor " + info.getServerId() + " - VC: " + snapshot.getClock());
                }
            } catch (Exception e) {
                // Servidor no disponible
            }
        }
        
        if (latestClock != null && serverCount > 0) {
            try {
                System.out.println("✅ Usando estado de " + serverCount + " servidores. VC final: " + latestClock);
                myServiceStub.becomeLeader(latestContent, latestClock);
            } catch (Exception e) {
                System.out.println("⚠️  Error al sincronizar estado: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️  No se pudo obtener estado de otros servidores. Usando estado local.");
        }
    }

    private void becomeLeaderNow() {
        System.out.println("\n👑 ========== SOY EL NUEVO LÍDER (ID " + state.getMyServerId() + ") ==========");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        // Notificar a TODOS los servidores (incluyendo con ID menor)
        List<CompletableFuture<Void>> notifications = new ArrayList<>();
        int notifiedCount = 0;
        
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
            notifiedCount++;
        }
        
        System.out.println("✅ Notificaciones enviadas a " + notifiedCount + " servidores.");
        
        // Pequeña espera para que las notificaciones se procesen
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                electionInProgress = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, ServerMain.GLOBAL_EXECUTOR);
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
    
    // **NUEVO MÉTODO:** Para iniciar elección desde otros componentes
    public void startElection() {
        if (!electionInProgress && !state.isLeader()) {
            startElectionOnStartup();
        }
    }
}