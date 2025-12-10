package server;

import common.IEditorService;

/**
 * Hilo encargado de comprobar periódicamente si el líder está vivo mediante
 * heartbeats. Si el líder no responde, se inicia automáticamente una elección
 * utilizando BullyElection.
 */
public class HeartbeatMonitor implements Runnable {

    private final EditorServiceImpl localServer;
    private final long intervalMillis;
    private final BullyElection election;

    private volatile boolean running = true;

    public HeartbeatMonitor(EditorServiceImpl localServer, long intervalMillis, BullyElection election) {
        this.localServer = localServer;
        this.intervalMillis = intervalMillis;
        this.election = election;
    }

    /** Permite detener el hilo de heartbeat. */
    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("[Server " + getIdSafe() + "] Heartbeat iniciado.");

        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException ignored) {}

            // Si es líder, no se realiza heartbeats a nadie
            if (localServer.isLeader()) {
                continue;
            }

            // Preguntar al módulo de elección quién es el líder actual
            RemoteServerInfo leaderInfo = election.getCurrentLeaderInfo();

            // Si no hay líder conocido, se inicia una elección
            if (leaderInfo == null) {
                System.out.println("[Server " + getIdSafe() + "] No hay líder conocido. Inicio elección.");
                election.startElection();
                continue;
            }

            // Intentar hacer ping al líder
            try {
                IEditorService leaderStub = leaderInfo.getStub();
                leaderStub.heartbeat();  // Si responde, el líder está vivo
            } catch (Exception e) {
                System.out.println("[Server " + getIdSafe() + "] Líder " + leaderInfo + " NO responde. Inicio Bully.");
                election.startElection();
            }
        }
    }

    /** Devuelve un ID seguro incluso si hay excepciones remotas. */
    private int getIdSafe() {
        try {
            return localServer.getServerId();
        } catch (Exception e) {
            return -1;
        }
    }
}
