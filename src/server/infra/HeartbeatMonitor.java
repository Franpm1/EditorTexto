package server.infra;

public class HeartbeatMonitor implements Runnable {
    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;
    private boolean initialized = false;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long interval) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = interval;
    }

    @Override
    public void run() {
        // ESPERAR MÁS PARA 6 SERVIDORES
        System.out.println("Monitor iniciado. Esperando 15s para estabilización..."); // 15s en lugar de 10s
        try { Thread.sleep(15000); } catch (InterruptedException e) {}
        System.out.println("Monitor activo. Comprobando líder...");
        
        while (true) {
            try { Thread.sleep(intervalMs); } catch (InterruptedException e) {}
            
            if (serverState.isLeader()) continue;

            RemoteServerInfo leader = bully.getCurrentLeaderInfo();
            if (leader == null) {
                System.out.println("No se conoce líder. Iniciando elección...");
                bully.onLeaderDown();
                continue;
            }
            
            try {
                leader.getStub().heartbeat();
                System.out.println("Lider " + leader.getServerId() + " responde");
            } catch (Exception e) {
                System.out.println("Lider " + leader.getServerId() + " NO responde");
                serverState.setCurrentLeaderId(-1);
                bully.onLeaderDown();
            }
        }
    }
}