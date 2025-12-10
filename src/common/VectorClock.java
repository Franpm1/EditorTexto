package common;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Permite al servidor y cliente saber el orden causal de los eventos.
 */
public class VectorClock implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final int[] vector;

    /**
     * Constructor vacío por defecto (para serialización o inits)
     */
    public VectorClock() {
        // Tamaño por defecto si no se especifica, o inicialización lazy
        this.vector = new int[0]; 
    }

    /**
     * @param size Número total de nodos en el sistema
     */
    public VectorClock(int size) {
        this.vector = new int[size];
    }

    // Constructor de copia
    public VectorClock(VectorClock other) {
        if (other != null && other.vector != null) {
            this.vector = Arrays.copyOf(other.vector, other.vector.length);
        } else {
            this.vector = new int[0];
        }
    }
    
    // Constructor desde array (usado en getClockCopy del Document)
    public VectorClock(int[] values) {
        this.vector = Arrays.copyOf(values, values.length);
    }

    /** * Evento Interno (Tick)
     */
    public synchronized void tick(int myId) {
        if (vector.length > myId && myId >= 0) {
            vector[myId]++;
        }
    }

    /**
     * Recepción de Mensaje (Merge).
     * Toma el máximo de cada componente.
     */
    public synchronized void merge(VectorClock other) {
        if (other == null || other.vector == null) return;
        
        // Si el otro reloj es más grande, redimensionamos (caso borde)
        // Pero asumiremos tamaño fijo por ahora para simplificar.
        int len = Math.min(this.vector.length, other.vector.length);
        
        for (int i = 0; i < len; i++) {
            this.vector[i] = Math.max(this.vector[i], other.vector[i]);
        }
    }

    /**
     * Sobrescribe este reloj con los valores de otro (Sync total).
     * Método necesario para Document.overwriteState
     */
    public synchronized void copyFrom(VectorClock other) {
        if (other == null || other.vector == null) return;
        
        // Copiamos valores uno a uno, o hacemos arraycopy si las longitudes coinciden
        for (int i = 0; i < this.vector.length && i < other.vector.length; i++) {
            this.vector[i] = other.vector[i];
        }
    }

    public synchronized int[] getValues() {
        return Arrays.copyOf(vector, vector.length);
    }
    
    public synchronized int[] getVectorCopy() {
        return getValues();
    }
    
    @Override
    public String toString() {
        return Arrays.toString(vector);
    }
}