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
    //Método para evitar que un líder nuevo formatee el documento comprobando cada elemento del vector
    //Por qué hago esto si puedo hacer un merge? Porque el merge no me dice si soy más nuevo o no, solo actualiza,
    //y yo necesito saber si soy más nuevo sin actualizarlo para copiar lo que hay en el documento!!
    public boolean isNewer(VectorClock other) {
        if (other == null) return true; //Soy el único, el más nuevo
        
        boolean someoneIsGreater = false; 
        int len = Math.min(this.vector.length, other.vector.length); //Evitar salir de rango de memoria
        
        for (int i = 0; i < len; i++) {
            if (this.vector[i] < other.vector[i]) {
                //relojes vectoriales, si algún componente es menor, no soy el más nuevo/actualizado
                return false; 
            }
            if (this.vector[i] > other.vector[i]) {
                someoneIsGreater = true;
            }
        }
        // Soy posterior si todos mis componentes son >= y al menos uno es >
        return someoneIsGreater;
    }
    
    public boolean isZero() { //
        for (int val : vector) {
            if (val > 0) return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return Arrays.toString(vector).replace(" ", ""); // [1,0,2]
    }
}