package server.infra;

public class HeartbeatMonitor implements Runnable {

    private final ServerState serverState;
    private final BullyElection bully;
    private final long intervalMs;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long intervalMs) {
        this.serverState = state;
        this.bully = bully;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        System.out.println("[HB] Monitor iniciado.");

        // Pequeña espera para que RMI se estabilice (no bloqueante)
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        while (true) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ignored) {}

            // Si soy líder, no hago heartbeat
            if (serverState.isLeader()) {
                continue;
            }

            RemoteServerInfo leaderInfo = bully.getCurrentLeaderInfo();

            // Si no hay líder conocido → elección
            if (leaderInfo == null) {
                System.out.println("[HB] No hay líder conocido. Lanzando elección.");
                bully.onLeaderDown();
                continue;
            }

            // Comprobar latido del líder
            try {
                leaderInfo.getStub().heartbeat();
                // System.out.println("[HB] Líder " + leaderInfo.getServerId() + " responde");
            } catch (Exception e) {
                System.out.println("[HB] Líder " + leaderInfo.getServerId() + " NO responde");
                serverState.setCurrentLeaderId(-1);
                bully.onLeaderDown();
            }
        }
    }
}
