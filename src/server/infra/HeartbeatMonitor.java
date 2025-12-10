package server.infra;

import common.IEditorService;

/**
 * Hilo que vigila al líder.
 * Si heartbeat() falla -> inicia elección local en BullyElection.
 */
public class HeartbeatMonitor implements Runnable {

    private final ServerState serverState;
    private final BullyElection bullyElection;
    private final long intervalMs;
    private volatile boolean running = true;

    public HeartbeatMonitor(ServerState serverState,
                            BullyElection bullyElection,
                            long intervalMs) {
        this.serverState = serverState;
        this.bullyElection = bullyElection;
        this.intervalMs = intervalMs;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {

        while (running) {
            try { Thread.sleep(intervalMs); }
            catch (InterruptedException ignored) {}

            // Soy líder -> no hago heartbeat
            if (serverState.isLeader()) continue;

            RemoteServerInfo leaderInfo = bullyElection.getCurrentLeaderInfo();

            // No se conoce líder (por ejemplo al inicio)
            if (leaderInfo == null) {
                bullyElection.onLeaderDown();
                continue;
            }

            try {
                IEditorService stub = leaderInfo.getStub();
                stub.heartbeat(); // <----------- ahora heartbeat()
            } catch(Exception e) {
                System.out.println("[Heartbeat] Líder no responde -> elección");
                bullyElection.onLeaderDown();
            }
        }
    }
}
