package server.infra;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mantiene el estado de este servidor en el cluster:
 * - si es LÍDER o BACKUP
 * - qué ID de servidor es el líder actual conocido.
 *
 * Esta clase es parte de la INFRAESTRUCTURA (Pareja C).
 */
public class ServerState {

    private final int myServerId;

    // ¿Soy líder?
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    // ID del líder actual conocido (puede ser yo mismo).
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1);

    public ServerState(int myServerId, boolean initiallyLeader) {
        this.myServerId = myServerId;
        this.isLeader.set(initiallyLeader);
        this.currentLeaderId.set(initiallyLeader ? myServerId : -1);
    }

    public int getMyServerId() {
        return myServerId;
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    /**
     * Cambia este servidor a LÍDER o BACKUP.
     * Normalmente lo invoca el BullyAlgorithm.
     */
    public void setLeader(boolean leader) {
        isLeader.set(leader);
        if (leader) {
            currentLeaderId.set(myServerId);
        }
        System.out.println("[Server " + myServerId + "] Ahora soy " + (leader ? "LÍDER" : "BACKUP"));
    }

    public int getCurrentLeaderId() {
        return currentLeaderId.get();
    }

    public void setCurrentLeaderId(int leaderId) {
        currentLeaderId.set(leaderId);
    }
}
