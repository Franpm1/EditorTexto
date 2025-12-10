package server.core;

import common.Operation;
import common.VectorClock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//Esta clase protege el StringBuilder y el VectorClock usando locks

public class Document {
    
    private final StringBuilder content;
    private final VectorClock vectorClock; // Nuestro reloj local
    private final int myId; // Nuestro ID de servidor
    
    // EL SEMÁFORO (ReentrantReadWriteLock)
    // - writeLock(): Solo deja pasar a UNO a la vez (para escribir).
    // - readLock(): Deja pasar a muchos a la vez (para leer/enviar a clientes).
    private final ReentrantReadWriteLock lock; 

    public Document(int myId, int totalNodes) {
        this.myId = myId;
        this.content = new StringBuilder();
        // Inicializamos nuestro reloj a ceros
        this.vectorClock = new VectorClock(totalNodes);
        this.lock = new ReentrantReadWriteLock();
    }

    public void applyOperation(Operation op) {
        //Pedimos permiso exclusivo para escribir
        lock.writeLock().lock(); 
        try {
            System.out.println("[Document] Procesando usuario " + op.getUserId() + "...");

            //CAUSALIDAD
            // 1. Merge: Actualizamos reloj
            vectorClock.merge(op.getClientClock());
            // 2. Tick: ha ocurrido un evento
            vectorClock.tick(myId);

            //GESTIÓN DE ESTADO DEL DOCUMENTO
            int pos = op.getPosition();
            
            //Comprobaciones de límites
            if (pos < 0) pos = 0;
            if (pos > content.length()) pos = content.length();
            //está insertado?true : false
            if (op.isInsert()) {
                content.insert(pos, op.getCharacter());
                System.out.println("Insertado: '" + op.getCharacter() + "' en " + pos);
            } else {
                if (pos < content.length()) { //Por si está vacío miro si hay algo
                    content.deleteCharAt(pos);
                    System.out.println("🗑️ Borrado en: " + pos);
                }
            }
            
            System.out.println("Nuevo Reloj Local: " + vectorClock.toString());
            System.out.println("Texto Actual: " + content.toString());

        } finally {
            lock.writeLock().unlock(); 
        }
    }

    // Lectura segura (permite múltiples lectores simultáneos)
    public String getContent() {
        lock.readLock().lock();
        try {
            return content.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Devuelve una copia del reloj actual
    public VectorClock getClockCopy() {
        lock.readLock().lock();
        try {
            // Usamos el constructor de copia que creamos antes
            return new VectorClock(vectorClock); 
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Cuando recibamos el estado completo de otro servidor (Sync)
    public void overwriteState(String newContent, VectorClock newClock) {
        lock.writeLock().lock();
        try {
            content.setLength(0);
            content.append(newContent);
            // Copiamos el reloj remoto al nuestro (Merge total o sobreescritura)
            // Para simplificar, hacemos merge, aunque un sync suele ser overwrite.
            // Aquí asumimos overwrite lógico del estado.
            // Truco: Hacemos un merge para asegurarnos de no ir atrás en el tiempo, 
            // o simplemente asignamos valores si tuviéramos un setter.
            // Por simplicidad usaremos merge, que es seguro.
            vectorClock.merge(newClock); 
            System.out.println("🔄 Estado sobrescrito/sincronizado.");
        } finally {
            lock.writeLock().unlock();
        }
    }
}