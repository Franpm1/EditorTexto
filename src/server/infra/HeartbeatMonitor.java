package server.infra;

import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 2; // Requerir 2 fallos consecutivos

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }

    @Override
    public void run() {
        System.out.println("Monitor de latidos iniciado (intervalo: " + intervalMs + "ms)");
        
        // Elección inicial en segundo plano
        if (!serverState.isLeader()) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // Esperar 1s antes de primera elección
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
                    System.out.println("⚠️  Sin líder conocido. Iniciando elección...");
                    CompletableFuture.runAsync(() -> {
                        bully.onLeaderDown();
                    }, ServerMain.GLOBAL_EXECUTOR);
                    consecutiveFailures = 0;
                }
                continue;
            }
            
            // Verificar líder en segundo plano
            final RemoteServerInfo currentLeader = leader;
            CompletableFuture.runAsync(() -> {
                try {
                    // Timeout MUY CORTO: 300ms
                    currentLeader.getStub().heartbeat();
                    
                    // ÉXITO: líder responde
                    consecutiveFailures = 0;
                    // System.out.println("Líder " + currentLeader.getServerId() + " responde OK");
                    
                } catch (Exception e) {
                    // FALLO: líder no responde
                    consecutiveFailures++;
                    System.out.println("❌ Líder " + currentLeader.getServerId() + 
                                     " no responde (" + consecutiveFailures + "/" + MAX_FAILURES + ")");
                    
                    if (consecutiveFailures >= MAX_FAILURES) {
                        System.out.println("🔥 LÍDER CAÍDO CONFIRMADO. Iniciando elección...");
                        serverState.setCurrentLeaderId(-1);
                        consecutiveFailures = 0;
                        
                        // Pequeña espera antes de elección (dar chance a que otros detecten)
                        try {
                            Thread.sleep(500);
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