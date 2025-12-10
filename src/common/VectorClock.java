package common;

import java.io.Serializable;
import java.util.Arrays;

//Permite al servidor y cliente saber el orden causal de los eventos, relojes vectoriales
 
public class VectorClock implements Serializable {
    
    //Para que Java permita enviarlo por RMI
    private static final long serialVersionUID = 1L;
    
    private final int[] vector;

    /**
     * @param size Número total de nodos en el sistema, por simplificar se usa tamaño fijo 
     */
    public VectorClock(int size) {
        this.vector = new int[size];
    }

    // Copia para que no se modifique el reloj original
    public VectorClock(VectorClock other) {
        this.vector = Arrays.copyOf(other.vector, other.vector.length);
    }

    /** Evento Interno (Tick)
     * Incrementamos nuestro propio índice
     * @param myId El ID del proceso que hace el tick
     */
    public synchronized void tick(int myId) {
        if (myId >= 0 && myId < vector.length) {
            vector[myId]++;
        }
    }

    /**Recepción de Mensaje (Merge).
     * Math.max = ¿Quién tiene el dato más actualizado?
     */
    public synchronized void merge(VectorClock other) {
        if (other == null) return;
        
        for (int i = 0; i < vector.length; i++) {
            // Por si los tamaños son diferentes
            if (i < other.vector.length) {
                this.vector[i] = Math.max(this.vector[i], other.vector[i]);
            }
        }
    }

    // Devuelve una copia para que nadie toque el array privado desde fuera
    public synchronized int[] getVectorCopy() {
        return Arrays.copyOf(vector, vector.length);
    }
    
    // Para imprimirlo bonito en logs: "[1, 2, 0]"
    @Override
    public String toString() {
        return Arrays.toString(vector);
    }
}