package server.infra;

import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 2; // Requerir 2 fallos consecutivos
    private long lastLeaderCheck = 0;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }

    @Override
    public void run() {
        System.out.println("❤️  Monitor de latidos iniciado (intervalo: " + intervalMs + "ms)");
        
        // Pequeña espera inicial para que todos los servidores arranquen
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Elección inicial en segundo plano (solo si no soy líder)
        if (!serverState.isLeader()) {
            System.out.println("🔍 Iniciando verificación inicial de líder...");
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // Esperar 1s antes de primera verificación
                    
                    // Primero verificar si ya hay un líder conocido
                    RemoteServerInfo knownLeader = bully.getCurrentLeaderInfo();
                    if (knownLeader != null) {
                        try {
                            knownLeader.getStub().heartbeat();
                            System.out.println("✅ Líder conocido " + knownLeader.getServerId() + " responde. Todo OK.");
                            return; // Ya hay líder funcionando
                        } catch (Exception e) {
                            System.out.println("⚠️  Líder conocido " + knownLeader.getServerId() + " no responde. Iniciando elección...");
                        }
                    }
                    
                    // Solo si no hay líder conocido, iniciar elección
                    bully.startElectionOnStartup();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, ServerMain.GLOBAL_EXECUTOR);
        }
        
        // Loop principal de monitoreo
        while (true) {
            try { 
                Thread.sleep(intervalMs); 
            } catch (InterruptedException e) {
                break;
            }
            
            // Si soy líder, no necesito monitorear a otros
            if (serverState.isLeader()) {
                consecutiveFailures = 0;
                continue;
            }
            
            RemoteServerInfo leader = bully.getCurrentLeaderInfo();
            if (leader == null) {
                // No hay líder conocido
                if (consecutiveFailures++ >= MAX_FAILURES) {
                    System.out.println("⚠️  Sin líder conocido por " + MAX_FAILURES + " checks. Iniciando elección...");
                    CompletableFuture.runAsync(() -> {
                        bully.onLeaderDown();
                    }, ServerMain.GLOBAL_EXECUTOR);
                    consecutiveFailures = 0;
                }
                continue;
            }
            
            // Prevenir checks demasiado frecuentes al mismo líder
            long now = System.currentTimeMillis();
            if (now - lastLeaderCheck < 1000) { // Máximo 1 check por segundo
                continue;
            }
            lastLeaderCheck = now;
            
            // Verificar líder en segundo plano
            final RemoteServerInfo currentLeader = leader;
            final int leaderId = currentLeader.getServerId();
            
            CompletableFuture.runAsync(() -> {
                try {
                    // Timeout CORTO pero no demasiado: 800ms
                    currentLeader.getStub().heartbeat();
                    
                    // ÉXITO: líder responde
                    consecutiveFailures = 0;
                    if (serverState.getCurrentLeaderId() != leaderId) {
                        serverState.setCurrentLeaderId(leaderId);
                        System.out.println("✅ Líder " + leaderId + " responde OK. Actualizado estado interno.");
                    }
                    
                } catch (Exception e) {
                    // FALLO: líder no responde
                    
                    // ***** VERIFICACIÓN CRÍTICA *****
                    // Antes de marcar como fallo, verificar si quizás YO soy el líder ahora
                    if (serverState.isLeader()) {
                        System.out.println("⚠️  Yo soy el líder ahora. Ignorando fallo de heartbeat.");
                        consecutiveFailures = 0;
                        return;
                    }
                    
                    // Verificar si el líder cambió entre tanto
                    if (serverState.getCurrentLeaderId() != leaderId) {
                        System.out.println("ℹ️  Líder cambió durante la verificación. Cancelando.");
                        consecutiveFailures = 0;
                        return;
                    }
                    
                    consecutiveFailures++;
                    System.out.println("❌ Líder " + leaderId + 
                                     " no responde (" + consecutiveFailures + "/" + MAX_FAILURES + ")");
                    
                    if (consecutiveFailures >= MAX_FAILURES) {
                        System.out.println("🔥 LÍDER CAÍDO CONFIRMADO (" + MAX_FAILURES + " fallos). Iniciando elección...");
                        
                        // Doble verificación antes de declarar caído
                        try {
                            Thread.sleep(500); // Pequeña pausa
                            currentLeader.getStub().heartbeat(); // Último intento
                            System.out.println("✅ ¡Líder " + leaderId + " responde después de todo! Cancelando elección.");
                            consecutiveFailures = 0;
                            return;
                        } catch (Exception e2) {
                            // Confirmado: líder caído
                        }
                        
                        serverState.setCurrentLeaderId(-1);
                        consecutiveFailures = 0;
                        
                        // Pequeña espera aleatoria para evitar elecciones simultáneas
                        try {
                            int randomWait = 500 + (int)(Math.random() * 1000); // 500-1500ms
                            Thread.sleep(randomWait);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        
                        bully.onLeaderDown();
                    }
                }
            }, ServerMain.GLOBAL_EXECUTOR);
        }
    }
}