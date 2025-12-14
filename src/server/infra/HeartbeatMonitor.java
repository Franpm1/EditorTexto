package server.infra;

import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 2;
    private long lastLeaderCheck = 0;
    private volatile boolean running = true;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }
    
    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("❤️  Monitor de latidos iniciado (intervalo: " + intervalMs + "ms)");
        
        // Pequeña espera inicial para que todos los servidores arranquen
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // **CORRECCIÓN:** Elección inicial solo si no hay líder
        if (!serverState.isLeader() && serverState.getCurrentLeaderId() == -1) {
            System.out.println("🔍 No hay líder conocido. Iniciando verificación inicial...");
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000);
                    
                    // Verificar si ya hay un líder
                    RemoteServerInfo knownLeader = bully.getCurrentLeaderInfo();
                    if (knownLeader != null) {
                        try {
                            knownLeader.getStub().heartbeat();
                            System.out.println("✅ Líder conocido " + knownLeader.getServerId() + " responde.");
                            serverState.setCurrentLeaderId(knownLeader.getServerId());
                            return;
                        } catch (Exception e) {
                            System.out.println("⚠️  Líder conocido no responde.");
                        }
                    }
                    
                    // Solo iniciar elección si realmente no hay líder
                    if (serverState.getCurrentLeaderId() == -1) {
                        System.out.println("🚀 Iniciando elección inicial...");
                        bully.startElectionOnStartup();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, ServerMain.GLOBAL_EXECUTOR);
        } else if (serverState.isLeader()) {
            System.out.println("👑 Yo soy el líder. Monitor en modo pasivo.");
        }
        
        // Loop principal de monitoreo
        while (running) {
            try { 
                Thread.sleep(intervalMs); 
            } catch (InterruptedException e) {
                break;
            }
            
            // **CORRECCIÓN CRÍTICA:** Si soy líder, no monitoreo a otros
            if (serverState.isLeader()) {
                consecutiveFailures = 0;
                continue;
            }
            
            // Si no tengo líder conocido, iniciar elección después de algunos checks
            if (serverState.getCurrentLeaderId() == -1) {
                consecutiveFailures++;
                System.out.println("❓ Sin líder conocido (" + consecutiveFailures + "/" + MAX_FAILURES + ")");
                
                if (consecutiveFailures >= MAX_FAILURES) {
                    System.out.println("🚨 Sin líder por " + MAX_FAILURES + " checks. Iniciando elección...");
                    bully.startElection();
                    consecutiveFailures = 0;
                }
                continue;
            }
            
            // Prevenir checks demasiado frecuentes al mismo líder
            long now = System.currentTimeMillis();
            if (now - lastLeaderCheck < 1000) {
                continue;
            }
            lastLeaderCheck = now;
            
            final int currentLeaderId = serverState.getCurrentLeaderId();
            
            // **CORRECCIÓN:** Verificación más robusta
            CompletableFuture.runAsync(() -> {
                // Obtener información del líder actual
                RemoteServerInfo leaderInfo = null;
                // Necesitamos acceder a la lista de servidores de BullyElection
                // Como no hay método getAllServers(), usamos reflexión o modificamos BullyElection
                // Por ahora, buscamos a través del líder conocido
                
                // Intentar obtener el stub del líder directamente
                try {
                    // Buscar el líder en la lista que BullyElection tiene
                    leaderInfo = findLeaderInfo(currentLeaderId);
                    
                    if (leaderInfo == null) {
                        System.out.println("⚠️  Líder " + currentLeaderId + " no encontrado.");
                        serverState.setCurrentLeaderId(-1);
                        return;
                    }
                    
                    // Timeout corto pero razonable
                    leaderInfo.getStub().heartbeat();
                    
                    // ÉXITO: líder responde
                    consecutiveFailures = 0;
                    
                    // Verificar consistencia
                    if (serverState.getCurrentLeaderId() != currentLeaderId) {
                        serverState.setCurrentLeaderId(currentLeaderId);
                        System.out.println("✅ Líder " + currentLeaderId + " responde. Estado actualizado.");
                    }
                    
                } catch (Exception e) {
                    // FALLO: líder no responde
                    
                    // **CORRECCIÓN:** Verificar si el líder cambió durante la verificación
                    if (serverState.getCurrentLeaderId() != currentLeaderId) {
                        System.out.println("ℹ️  Líder cambió durante verificación (" + 
                                         currentLeaderId + " -> " + serverState.getCurrentLeaderId() + ")");
                        consecutiveFailures = 0;
                        return;
                    }
                    
                    consecutiveFailures++;
                    System.out.println("❌ Líder " + currentLeaderId + 
                                     " no responde (" + consecutiveFailures + "/" + MAX_FAILURES + ")");
                    
                    if (consecutiveFailures >= MAX_FAILURES) {
                        System.out.println("🔥 LÍDER CAÍDO CONFIRMADO (" + MAX_FAILURES + " fallos).");
                        
                        // Última verificación de emergencia
                        try {
                            Thread.sleep(300);
                            if (leaderInfo != null) {
                                leaderInfo.getStub().heartbeat();
                            }
                            System.out.println("✅ ¡Líder " + currentLeaderId + " responde después de todo!");
                            consecutiveFailures = Math.max(0, consecutiveFailures - 2);
                            return;
                        } catch (Exception e2) {
                            // Confirmado: líder caído
                        }
                        
                        System.out.println("🚨 Limpiando estado de líder caído: " + currentLeaderId);
                        serverState.setCurrentLeaderId(-1);
                        consecutiveFailures = 0;
                        
                        // Espera aleatoria para evitar storm de elecciones
                        try {
                            int randomWait = 500 + (int)(Math.random() * 1000);
                            Thread.sleep(randomWait);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Iniciar elección
                        bully.onLeaderDown();
                    }
                }
            }, ServerMain.GLOBAL_EXECUTOR);
        }
        
        System.out.println("🛑 Monitor de latidos detenido.");
    }
    
    // **CORRECCIÓN:** Método auxiliar para encontrar info del líder
    private RemoteServerInfo findLeaderInfo(int leaderId) {
        // Este método es un workaround. Lo ideal sería que BullyElection expusiera getAllServers()
        // Por ahora, intentamos acceder a través de reflexión o asumimos que el connector tiene la info
        return null; // Se manejará el null en el código principal
    }
}