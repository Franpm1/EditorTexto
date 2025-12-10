package server.infra;

import common.IEditorService;


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
            } catch (InterruptedException ignored) {}

            if (serverState.isLeader()) {
                // Si soy líder no me vigilo a mí mismo
                continue;
            }

            RemoteServerInfo leaderInfo = bullyElection.getCurrentLeaderInfo();
            if (leaderInfo == null) {
                System.out.println("[Server " + myId + "] No hay líder conocido. Inicio elección.");
                bullyElection.startLocalElection();
                continue;
            }

            try {
                IEditorService leaderStub = leaderInfo.getStub();
                leaderStub.ping();   // <-- AQUÍ USAMOS TU ping()
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] El líder " + leaderInfo + " NO responde. Inicio Bully.");
                bullyElection.startLocalElection();
            }
        }
    }
}
