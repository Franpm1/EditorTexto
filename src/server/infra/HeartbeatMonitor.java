package server.infra;

import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }

    @Override
    public void run() {
        System.out.println("Monitor iniciado. Buscando líder...");
        
        // 1. ELECCIÓN INMEDIATA al iniciar (en segundo plano)
        if (!serverState.isLeader()) {
            CompletableFuture.runAsync(() -> {
                bully.startElectionOnStartup();
            }, ServerMain.GLOBAL_EXECUTOR);
        }
        
        // 2. Monitoreo periódico NO BLOQUEANTE
        while (true) {
            try { 
                Thread.sleep(intervalMs); 
            } catch (InterruptedException e) {
                break;
            }
            
            if (serverState.isLeader()) continue;

            RemoteServerInfo leader = bully.getCurrentLeaderInfo();
            if (leader == null) {
                // Líder desconocido - elección en segundo plano
                CompletableFuture.runAsync(() -> {
                    bully.onLeaderDown();
                }, ServerMain.GLOBAL_EXECUTOR);
                continue;
            }
            
            // Verificar líder EN SEGUNDO PLANO (no bloquear loop principal)
            CompletableFuture.runAsync(() -> {
                try {
                    leader.getStub().heartbeat();
                    // Líder responde - todo bien
                } catch (Exception e) {
                    // Líder no responde - iniciar elección
                    System.out.println("Líder " + leader.getServerId() + " NO responde");
                    serverState.setCurrentLeaderId(-1);
                    bully.onLeaderDown();
                }
            }, ServerMain.GLOBAL_EXECUTOR);
        }
    }
}