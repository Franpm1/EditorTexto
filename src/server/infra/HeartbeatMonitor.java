package server.infra;

import common.IEditorService;

/**
 * Heartbeat:
 * - Backups hacen heartbeat al líder cada intervalMs.
 * - Si falla: limpiar currentLeaderId y disparar elección.
 */
public class HeartbeatMonitor implements Runnable {

    private final ServerState state;
    private final BullyElection bully;
    private final long intervalMs;
    private volatile boolean running = true;

    public HeartbeatMonitor(ServerState state, BullyElection bully, long intervalMs) {
        this.state = state;
        this.bully = bully;
        this.intervalMs = intervalMs;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        while (running) {
            try { Thread.sleep(intervalMs); }
            catch (InterruptedException ignored) {}

            // Si soy líder, no vigilo a nadie.
            if (state.isLeader()) continue;

            RemoteServerInfo leaderInfo = bully.getCurrentLeaderInfo();

            // Si no hay líder conocido, intento elegir.
            if (leaderInfo == null) {
                state.setCurrentLeaderId(-1);
                bully.onLeaderDown();
                continue;
            }

            try {
                IEditorService leaderStub = leaderInfo.getStub();
                leaderStub.heartbeat();
            } catch (Exception e) {
                System.out.println("[Heartbeat] El líder no responde. Inicio elección.");
                // CLAVE: si no limpiamos, el Bully puede "no arrancar"
                state.setCurrentLeaderId(-1);
                bully.onLeaderDown();
            }
        }
    }
}
