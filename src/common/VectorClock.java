package common;

import java.io.Serializable;
import java.util.Arrays;

public class VectorClock implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int[] vector;
    private final long timestamp; // **NUEVO:** Para desempate en casos concurrentes

    public VectorClock(int size) {
        this.vector = new int[size];
        this.timestamp = System.currentTimeMillis();
    }

    public VectorClock(VectorClock other) {
        this.vector = Arrays.copyOf(other.vector, other.vector.length);
        this.timestamp = other.timestamp;
    }
    
    public VectorClock(int[] values) {
        this.vector = Arrays.copyOf(values, values.length);
        this.timestamp = System.currentTimeMillis();
    }

    public synchronized void tick(int myId) {
        if (myId >= 0 && myId < vector.length) vector[myId]++;
    }

    public synchronized void merge(VectorClock other) {
        if (other == null) return;
        for (int i = 0; i < vector.length; i++) {
            if (i < other.vector.length) {
                this.vector[i] = Math.max(this.vector[i], other.vector[i]);
            }
        }
    }
    
    public synchronized void copyFrom(VectorClock other) {
        if (other == null) return;
        for (int i = 0; i < vector.length && i < other.vector.length; i++) {
            this.vector[i] = other.vector[i];
        }
    }

    // **MEJORADO:** Comparación más robusta
    public synchronized boolean isNewerThan(VectorClock other) {
        if (other == null) return true;
        
        boolean atLeastOneGreater = false;
        boolean atLeastOneLess = false;
        boolean allEqual = true;
        
        int minLength = Math.min(this.vector.length, other.vector.length);
        
        for (int i = 0; i < minLength; i++) {
            if (this.vector[i] > other.vector[i]) {
                atLeastOneGreater = true;
                allEqual = false;
            } else if (this.vector[i] < other.vector[i]) {
                atLeastOneLess = true;
                allEqual = false;
            }
        }
        
        // **MEJORA 1:** Si todos son iguales, comparar por timestamp
        if (allEqual) {
            return this.timestamp > other.timestamp;
        }
        
        // **MEJORA 2:** Si hay conflicto (concurrente), usar timestamp como desempate
        if (atLeastOneGreater && atLeastOneLess) {
            // Estados concurrentes - usar timestamp
            return this.timestamp > other.timestamp;
        }
        
        // Caso normal: causalmente posterior
        return atLeastOneGreater && !atLeastOneLess;
    }
    
    // **NUEVO MÉTODO:** Para verificar igualdad
    public synchronized boolean equals(VectorClock other) {
        if (other == null) return false;
        if (this.vector.length != other.vector.length) return false;
        
        for (int i = 0; i < this.vector.length; i++) {
            if (this.vector[i] != other.vector[i]) {
                return false;
            }
        }
        return true;
    }
    
    // **NUEVO MÉTODO:** Para casos concurrentes (conflicto)
    public synchronized boolean isConcurrentWith(VectorClock other) {
        if (other == null) return false;
        
        boolean greater = false;
        boolean less = false;
        
        int minLength = Math.min(this.vector.length, other.vector.length);
        
        for (int i = 0; i < minLength; i++) {
            if (this.vector[i] > other.vector[i]) {
                greater = true;
            } else if (this.vector[i] < other.vector[i]) {
                less = true;
            }
        }
        
        return greater && less; // Verdaderamente concurrente
    }

    @Override
    public String toString() {
        return Arrays.toString(vector).replace(" ", "") + " @ " + timestamp;
    }
    
    // **NUEVO:** Getter para timestamp
    public long getTimestamp() {
        return timestamp;
    }
}