package server.core;
import common.Operation;
import common.VectorClock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Document {
    private final StringBuilder content = new StringBuilder();
    private final VectorClock vectorClock;
    private final int myId;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Document(int myId, int totalNodes) {
        this.myId = myId;
        this.vectorClock = new VectorClock(totalNodes);
    }

    public void applyOperation(Operation op) {
        lock.writeLock().lock();
        try {
            if (op.getVectorClock() != null) vectorClock.merge(op.getVectorClock());
            vectorClock.tick(myId);

            int pos = Math.max(0, Math.min(op.getPosition(), content.length()));

            if ("INSERT".equalsIgnoreCase(op.getType())) {
                content.insert(pos, op.getText());
            } else if ("DELETE".equalsIgnoreCase(op.getType())) {
                // CORREGIDO: Borrado de rango
                int len = op.getText().length(); 
                int end = Math.min(pos + len, content.length());
                if (pos < content.length()) content.delete(pos, end);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getContent() {
        lock.readLock().lock();
        try { return content.toString(); } finally { lock.readLock().unlock(); }
    }

    public VectorClock getClockCopy() {
        lock.readLock().lock();
        try { return new VectorClock(vectorClock); } finally { lock.readLock().unlock(); }
    }

    public void overwriteState(String newContent, VectorClock newClock) {
        lock.writeLock().lock();
        try {
            content.setLength(0);
            content.append(newContent);
            vectorClock.copyFrom(newClock);
        } finally {
            lock.writeLock().unlock();
        }
    }
}