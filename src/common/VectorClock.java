package common;

import java.io.Serializable;
import java.util.Arrays;

public class VectorClock implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int[] vector;

    public VectorClock(int size) {
        this.vector = new int[size];
    }

    public VectorClock(VectorClock other) {
        this.vector = Arrays.copyOf(other.vector, other.vector.length);
    }
    
    public VectorClock(int[] values) {
        this.vector = Arrays.copyOf(values, values.length);
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

    // MÉTODO NUEVO: Comparación eficiente
    public synchronized boolean isNewerThan(VectorClock other) {
        if (other == null) return true;
        
        boolean atLeastOneGreater = false;
        boolean atLeastOneLess = false;
        
        int minLength = Math.min(this.vector.length, other.vector.length);
        
        for (int i = 0; i < minLength; i++) {
            if (this.vector[i] > other.vector[i]) atLeastOneGreater = true;
            if (this.vector[i] < other.vector[i]) atLeastOneLess = true;
        }
        
        return atLeastOneGreater && !atLeastOneLess;
    }

    @Override
    public String toString() {
        return Arrays.toString(vector).replace(" ", "");
    }
}