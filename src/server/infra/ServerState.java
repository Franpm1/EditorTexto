package server.infra;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerState {
    private final int myServerId;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1); // Iniciar en -1

    public ServerState(int myServerId, boolean initiallyLeader) {
        this.myServerId = myServerId;
        this.isLeader.set(initiallyLeader);
        // NO hardcodear líder inicial
        if (initiallyLeader) {
            this.currentLeaderId.set(myServerId);
        }
        // else: se quedará en -1 hasta que se detecte líder
    }
    public int getMyServerId() { return myServerId; }
    public boolean isLeader() { return isLeader.get(); }
    public void setLeader(boolean leader) {
        isLeader.set(leader);
        if (leader) currentLeaderId.set(myServerId);
    }
    public int getCurrentLeaderId() { return currentLeaderId.get(); }
    public void setCurrentLeaderId(int id) { currentLeaderId.set(id); }
}