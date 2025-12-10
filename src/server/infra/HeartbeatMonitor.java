package server.infra;

import common.IEditorService;

/**
 * Hilo de INFRAESTRUCTURA que vigila al líder mediante heartbeats.
 *
 * Funciona así:
 *  - Si este servidor es BACKUP:
 *      - cada X ms hace ping (heartbeat()) al líder actual.
 *      - si falla, lanza el BullyAlgorithm (startElection()).
 *  - Si este servidor es LÍDER:
 *      - no hace nada (no se pinge a sí mismo).
 */
public class HeartbeatMonitor implements Runnable {

    private final ServerState serverState;
    private final BullyElection bullyElection;
    private final long intervalMillis;

    private volatile boolean running = true;

    public HeartbeatMonitor(ServerState serverState,
                            BullyElection bullyElection,
                            long intervalMillis) {
        this.serverState = serverState;
        this.bullyElection = bullyElection;
        this.intervalMillis = intervalMillis;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        int myId = serverState.getMyServerId();
        System.out.println("[Server " + myId + "] HeartbeatMonitor iniciado.");

        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException ignored) {
            }

            // Si soy líder, no necesito hacer ping a nadie
            if (serverState.isLeader()) {
                continue;
            }

            RemoteServerInfo leaderInfo = bullyElection.getCurrentLeaderInfo();
            if (leaderInfo == null) {
                // No sabemos quién es el líder → iniciar elección
                System.out.println("[Server " + myId + "] No hay líder conocido. Inicio elección.");
                bullyElection.startElection();
                continue;
            }

            try {
                IEditorService leaderStub = leaderInfo.getStub();
                leaderStub.heartbeat(); // si lanza excepción, el líder ha caído
                // System.out.println("[Server " + myId + "] Líder " + leaderInfo.getServerId() + " OK.");
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] Líder " + leaderInfo + " NO responde. Inicio Bully.");
                bullyElection.startElection();
            }
        }
    }
}
