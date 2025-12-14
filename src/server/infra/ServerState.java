package server.infra;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerState {
    private final int myServerId;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1);
    private final AtomicBoolean electionTriggered = new AtomicBoolean(false);
    private final AtomicBoolean isCandidate = new AtomicBoolean(false); // **NUEVO:** Estado candidato
    private final AtomicInteger leaderTerm = new AtomicInteger(0); // **NUEVO:** Término/generación

    public ServerState(int myServerId, boolean initiallyLeader) {
        this.myServerId = myServerId;
        this.isLeader.set(initiallyLeader);
        if (initiallyLeader) {
            this.currentLeaderId.set(myServerId);
            this.leaderTerm.incrementAndGet();
        }
    }
    
    public int getMyServerId() { return myServerId; }
    public boolean isLeader() { return isLeader.get(); }
    
    // **MEJORADO:** Transición atómica a líder
    public synchronized boolean becomeLeader() {
        if (isCandidate.compareAndSet(true, false)) {
            isLeader.set(true);
            currentLeaderId.set(myServerId);
            leaderTerm.incrementAndGet();
            System.out.println("🎉 Convertido en líder. Término: " + leaderTerm.get());
            return true;
        }
        return false;
    }
    
    // **NUEVO:** Convertirse en candidato
    public synchronized boolean becomeCandidate() {
        if (!isLeader.get() && !isCandidate.get() && currentLeaderId.get() == -1) {
            isCandidate.set(true);
            System.out.println("🗳️  Convertido en candidato para elección.");
            return true;
        }
        return false;
    }
    
    // **NUEVO:** Abandonar candidatura
    public synchronized void abandonCandidacy() {
        isCandidate.set(false);
    }
    
    public void setLeader(boolean leader) {
        if (leader) {
            becomeLeader();
        } else {
            isLeader.set(false);
            if (currentLeaderId.get() == myServerId) {
                currentLeaderId.set(-1);
            }
        }
    }
    
    public int getCurrentLeaderId() { return currentLeaderId.get(); }
    
    // **MEJORADO:** Con validación de término
    public synchronized void setCurrentLeaderId(int newLeaderId) { 
        if (newLeaderId == myServerId) {
            // Me asignan a mí como líder
            becomeLeader();
        } else if (newLeaderId == -1) {
            // Limpiar líder
            currentLeaderId.set(-1);
            isLeader.set(false);
            isCandidate.set(false);
        } else {
            // Asignar otro líder
            if (newLeaderId > currentLeaderId.get() || currentLeaderId.get() == -1) {
                currentLeaderId.set(newLeaderId);
                isLeader.set(false);
                isCandidate.set(false);
                System.out.println("✅ Nuevo líder establecido: " + newLeaderId);
            } else if (newLeaderId < currentLeaderId.get()) {
                System.out.println("⚠️  Ignorando líder con ID menor: " + newLeaderId + 
                                 " < " + currentLeaderId.get());
            }
        }
    }
    
    // **NUEVO:** Obtener término actual
    public int getLeaderTerm() {
        return leaderTerm.get();
    }
    
    // **NUEVO:** Verificar si soy candidato
    public boolean isCandidate() {
        return isCandidate.get();
    }
    
    public boolean triggerElection() {
        return electionTriggered.compareAndSet(false, true);
    }
    
    public void resetElectionTrigger() {
        electionTriggered.set(false);
    }
}