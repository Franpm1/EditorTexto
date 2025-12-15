package server.infra;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;
    private boolean electionDone = false;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }

    @Override
    public void run() {
        System.out.println("Monitor iniciado (intervalo: " + intervalMs + "ms)");
        
        // Elección inmediata pero con breve pausa para que todos arranquen
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        
        if (!serverState.isLeader()) {
            bully.startElectionOnStartup();
        }
        
        // Monitoreo más rápido
        while (true) {
            try { Thread.sleep(intervalMs); } catch (InterruptedException e) {}
            
            if (serverState.isLeader()) continue;

            RemoteServerInfo leader = bully.getCurrentLeaderInfo();
            if (leader == null) {
                System.out.println("No hay líder, reiniciando elección");
                bully.onLeaderDown();
                continue;
            }
            
            try {
                // Heartbeat rápido
                leader.getStub().heartbeat();
            } catch (Exception e) {
                System.out.println("El líder no responde");
                serverState.setCurrentLeaderId(-1);
                bully.onLeaderDown();
            }
        }
    }
}