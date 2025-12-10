package server.core;

import common.Operation;
import common.VectorClock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Document {

    private final StringBuilder content;
    private final VectorClock vectorClock; // Reloj local del servidor
    private final int myId; 

    // Semáforo para controlar acceso concurrente
    private final ReentrantReadWriteLock lock; 

    public Document(int myId, int totalNodes) {
        this.myId = myId;
        this.content = new StringBuilder();
        // Usamos el constructor de tamaño fijo definido en common.VectorClock
        this.vectorClock = new VectorClock(totalNodes);
        this.lock = new ReentrantReadWriteLock();
    }

    public void applyOperation(Operation op) {
        // Bloqueo de ESCRITURA: Solo uno pasa a la vez
        lock.writeLock().lock(); 
        try {
            System.out.println("[Document] Procesando op de Usuario: " + op.getOwner());

            // 1. CAUSALIDAD: Merge y Tick
            if (op.getVectorClock() != null) {
                vectorClock.merge(op.getVectorClock());
            }
            vectorClock.tick(myId);

            // 2. APLICAR CAMBIOS AL TEXTO
            int pos = op.getPosition();
            String text = op.getText(); // Ahora es String, no char

            // Protección de límites básicos
            if (pos < 0) pos = 0;
            if (pos > content.length()) pos = content.length();

            if ("INSERT".equalsIgnoreCase(op.getType())) {
                content.insert(pos, text);
                System.out.println("   -> Insertado: '" + text + "' en " + pos);

            } else if ("DELETE".equalsIgnoreCase(op.getType())) {
                // Borramos la longitud del texto que viene en la operación
                int lengthToDelete = text.length(); 
                int end = Math.min(pos + lengthToDelete, content.length());

                if (pos < content.length()) {
                    content.delete(pos, end);
                    System.out.println("   -> Borrado desde " + pos + " hasta " + end);
                }
            }

            System.out.println("   -> Estado Actual: " + content.toString());
            System.out.println("   -> Nuevo Reloj: " + vectorClock.toString());

        } finally {
            lock.writeLock().unlock(); 
        }
    }

    // Lectura segura
    public String getContent() {
        lock.readLock().lock();
        try {
            return content.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    public VectorClock getClockCopy() {
        lock.readLock().lock();
        try {
            // Usamos el constructor que acepta int[] definido en VectorClock
            return new VectorClock(vectorClock.getValues()); 
        } finally {
            lock.readLock().unlock();
        }
    }

    // Usado para sincronización total (Replicación o al convertirse en Líder)
    public void overwriteState(String newContent, VectorClock newClock) {
        lock.writeLock().lock();
        try {
            content.setLength(0);
            content.append(newContent);

            // Actualizamos nuestro reloj para estar sincronizados
            if (newClock != null) {
                vectorClock.copyFrom(newClock); 
            }

            System.out.println(" [SYNC] Estado interno sobrescrito con éxito.");
        } finally {
            lock.writeLock().unlock();
        }
    }
}