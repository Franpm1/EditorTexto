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
        System.out.println("Monitor iniciado. Buscando líder...");
        
        // 1. ELECCIÓN INMEDIATA al iniciar
        if (!serverState.isLeader()) {
            System.out.println("Iniciando elección al arrancar...");
            bully.startElectionOnStartup();
        }
        
        // 2. Monitoreo periódico
        while (true) {
            try { Thread.sleep(intervalMs); } catch (InterruptedException e) {}
            
            if (serverState.isLeader()) continue;

            RemoteServerInfo leader = bully.getCurrentLeaderInfo();
            if (leader == null) {
                System.out.println("No hay líder conocido. Reiniciando elección...");
                bully.onLeaderDown();
                continue;
            }
            
            try {
                leader.getStub().heartbeat();
                System.out.println("Líder " + leader.getServerId() + " responde");
            } catch (Exception e) {
                System.out.println("Líder " + leader.getServerId() + " NO responde");
                serverState.setCurrentLeaderId(-1);
                bully.onLeaderDown();
            }
        }
    }
}