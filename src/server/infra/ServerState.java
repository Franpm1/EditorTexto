package server.infra;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerState {
    private final int myServerId;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1);
    private final AtomicBoolean electionTriggered = new AtomicBoolean(false);

    public ServerState(int myServerId, boolean initiallyLeader) {
        this.myServerId = myServerId;
        this.isLeader.set(initiallyLeader);
        if (initiallyLeader) {
            this.currentLeaderId.set(myServerId);
        }
    }
    
    public int getMyServerId() { return myServerId; }
    public boolean isLeader() { return isLeader.get(); }
    
    public void setLeader(boolean leader) {
        isLeader.set(leader);
        if (leader) {
            currentLeaderId.set(myServerId);
        } else if (currentLeaderId.get() == myServerId) {
            // Si dejo de ser líder, limpiar el ID de líder
            currentLeaderId.set(-1);
        }
    }
    
    public int getCurrentLeaderId() { return currentLeaderId.get(); }
    
    public void setCurrentLeaderId(int id) { 
        // Solo aceptar nuevo líder si es diferente y no soy yo (a menos que sea -1)
        if (id != myServerId || id == -1) {
            currentLeaderId.set(id);
            
            // Si me asignan como líder a mí mismo, marcar isLeader=true
            if (id == myServerId) {
                isLeader.set(true);
            } else if (id != -1) {
                // Si asignan otro líder, yo NO soy líder
                isLeader.set(false);
            }
        }
    }
    
    public boolean triggerElection() {
        return electionTriggered.compareAndSet(false, true);
    }
    
    public void resetElectionTrigger() {
        electionTriggered.set(false);
    }
}