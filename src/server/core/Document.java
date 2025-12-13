package server.core;

import common.Operation;
import common.VectorClock;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Document {

    // ---- Estado en memoria ----
    private final StringBuilder content = new StringBuilder();
    private final VectorClock vectorClock;
    private final int myId;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ---- Persistencia ----
    private final Path dataDir;
    private final Path snapshotFile;
    private final Path walFile;
    private int opsSinceSnapshot = 0;
    private final int snapshotEveryNOps;

    // Snapshot serializable
    private static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        final String content;
        final VectorClock clock;
        Snapshot(String content, VectorClock clock) {
            this.content = content;
            this.clock = clock;
        }
    }

    // ObjectOutputStream que permite append sin reescribir header
    private static final class AppendingObjectOutputStream extends ObjectOutputStream {
        AppendingObjectOutputStream(OutputStream out) throws IOException { super(out); }
        @Override protected void writeStreamHeader() throws IOException { reset(); }
    }

    /**
     * Constructor compatible con la versión anterior (sin tocar ServerMain antiguo).
     * Usa por defecto:
     *  - dataDir: ./data/server-<id>/
     *  - snapshot cada 50 operaciones.
     */
    public Document(int myId, int totalNodes) {
        this(myId, totalNodes, Path.of("data"), 50);
    }

    public Document(int myId, int totalNodes, Path baseDataDir, int snapshotEveryNOps) {
        this.myId = myId;
        this.vectorClock = new VectorClock(totalNodes);
        this.snapshotEveryNOps = Math.max(1, snapshotEveryNOps);

        this.dataDir = baseDataDir.resolve("server-" + myId);
        this.snapshotFile = dataDir.resolve("snapshot.dat");
        this.walFile = dataDir.resolve("wal.log");

        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new RuntimeException("No puedo crear data dir: " + this.dataDir, e);
        }
    }

    // -------------------- Operaciones en memoria --------------------

    public void applyOperation(Operation op) {
        lock.writeLock().lock();
        try {
            if (op.getVectorClock() != null) vectorClock.merge(op.getVectorClock());
            vectorClock.tick(myId);

            int pos = Math.max(0, Math.min(op.getPosition(), content.length()));

            if ("INSERT".equalsIgnoreCase(op.getType())) {
                content.insert(pos, op.getText());
            } else if ("DELETE".equalsIgnoreCase(op.getType())) {
                // borrado de rango, usando longitud del "text" como dummy
                int len = (op.getText() == null) ? 0 : op.getText().length();
                int end = Math.min(pos + len, content.length());
                if (pos < content.length() && end >= pos) {
                    content.delete(pos, end);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getContent() {
        lock.readLock().lock();
        try { return content.toString(); }
        finally { lock.readLock().unlock(); }
    }

    public VectorClock getClockCopy() {
        lock.readLock().lock();
        try { return new VectorClock(vectorClock); }
        finally { lock.readLock().unlock(); }
    }

    public void overwriteState(String newContent, VectorClock newClock) {
        lock.writeLock().lock();
        try {
            content.setLength(0);
            content.append(newContent == null ? "" : newContent);
            if (newClock != null) vectorClock.copyFrom(newClock);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------- Persistencia: WAL --------------------

    /**
     * Añade la operación al WAL (durabilidad). Debe llamarse ANTES de applyOperation().
     */
    public void appendToWAL(Operation op) throws IOException {
        Files.createDirectories(dataDir);

        boolean exists = Files.exists(walFile) && Files.size(walFile) > 0;
        try (OutputStream os = new BufferedOutputStream(
                Files.newOutputStream(walFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND));
             ObjectOutputStream oos = exists ? new AppendingObjectOutputStream(os) : new ObjectOutputStream(os)) {
            oos.writeObject(op);
        }

        opsSinceSnapshot++;
    }

    public List<Operation> loadWAL() throws IOException {
        List<Operation> ops = new ArrayList<>();
        if (!Files.exists(walFile) || Files.size(walFile) == 0) return ops;

        try (InputStream is = new BufferedInputStream(Files.newInputStream(walFile));
             ObjectInputStream ois = new ObjectInputStream(is)) {

            while (true) {
                Object obj = ois.readObject();
                if (obj instanceof Operation op) ops.add(op);
            }

        } catch (EOFException eof) {
            // fin normal
        } catch (ClassNotFoundException cnf) {
            throw new IOException("Clase no encontrada al leer WAL", cnf);
        }
        return ops;
    }

    public void clearWAL() throws IOException {
        if (Files.exists(walFile)) Files.delete(walFile);
        opsSinceSnapshot = 0;
    }

    // -------------------- Persistencia: Snapshot --------------------

    /**
     * Guarda snapshot atómico y limpia WAL (porque el estado completo ya está en snapshot).
     */
    public void saveSnapshot() throws IOException {
        Files.createDirectories(dataDir);

        String snapContent;
        VectorClock snapClock;

        lock.readLock().lock();
        try {
            snapContent = content.toString();
            snapClock = new VectorClock(vectorClock);
        } finally {
            lock.readLock().unlock();
        }

        Snapshot snapshot = new Snapshot(snapContent, snapClock);

        Path tmp = snapshotFile.resolveSibling(snapshotFile.getFileName() + ".tmp");
        try (OutputStream os = new BufferedOutputStream(
                Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
             ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(snapshot);
        }
        Files.move(tmp, snapshotFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        clearWAL();
    }

    public void loadSnapshotIfExists() throws IOException {
        if (!Files.exists(snapshotFile) || Files.size(snapshotFile) == 0) return;

        try (InputStream is = new BufferedInputStream(Files.newInputStream(snapshotFile));
             ObjectInputStream ois = new ObjectInputStream(is)) {

            Object obj = ois.readObject();
            if (obj instanceof Snapshot snap) {
                overwriteState(snap.content, snap.clock);
            }

        } catch (ClassNotFoundException cnf) {
            throw new IOException("Clase no encontrada al leer Snapshot", cnf);
        }
    }

    /**
     * Recuperación al arrancar:
     *  - carga snapshot
     *  - reproduce WAL
     *
     * NO borra WAL aquí; se borra al hacer saveSnapshot() o al recibir overwrite completo.
     */
    public void recoverFromDisk() throws IOException {
        loadSnapshotIfExists();
        List<Operation> ops = loadWAL();
        for (Operation op : ops) {
            applyOperation(op);
        }
    }

    public boolean shouldSnapshot() {
        return opsSinceSnapshot >= snapshotEveryNOps;
    }
}
