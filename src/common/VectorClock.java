package common;

import java.io.Serializable;
import java.util.Arrays;

public class VectorClock implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int[] vector;

    public VectorClock(int size) {
        this.vector = new int[size];
    }

    // Constructor de copia vital para snapshots
    public VectorClock(VectorClock other) {
        this.vector = Arrays.copyOf(other.vector, other.vector.length);
    }
    
    // Constructor para mocks/tests
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
    
    // Método para sobreescribir el estado (Sync)
    public synchronized void copyFrom(VectorClock other) {
        if (other == null) return;
        for (int i = 0; i < vector.length && i < other.vector.length; i++) {
            this.vector[i] = other.vector[i];
        }
    }

    // NUEVO: Getter para acceso eficiente (usado por VectorClockComparator)
    public synchronized int[] getVector() {
        return Arrays.copyOf(vector, vector.length); // Devuelve copia por seguridad
    }

    @Override
    public String toString() {
        return Arrays.toString(vector).replace(" ", ""); // [1,0,2]
    }
}