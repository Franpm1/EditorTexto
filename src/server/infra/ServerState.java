package server.infra;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerState {
    private final int myServerId;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1);

    public ServerState(int myServerId, boolean initiallyLeader) {
        this.myServerId = myServerId;
        this.isLeader.set(initiallyLeader);
        // FIX: Siempre servidor 0 es líder inicial
        this.currentLeaderId.set(0);
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