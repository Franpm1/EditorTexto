package common;

import java.io.Serializable;

/**
 * Contiene qué tecla se pulsó, dónde, quién fue y el reloj
 * Implementa Serializable para poder viajar por RMI.
 */
public class Operation implements Serializable {
    
    private static final long serialVersionUID = 1L; //rmi


    private final boolean insert; // true = insertar, false = borrar
    private final char character;
    private final int position;
    private final int userId;       // Quién hizo el cambio
    private final VectorClock clientClock; // El reloj del cliente en ese momento

    public Operation(boolean insert, char character, int position, int userId, VectorClock clientClock) {
        this.insert = insert;
        this.character = character;
        this.position = position;
        this.userId = userId;
        this.clientClock = clientClock; 
    }

    // Getters necesarios para que el Servidor pueda leer los datos
    public boolean getInsert() { return insert; }
    public char getCharacter() { return character; }
    public int getPosition() { return position; }
    public int getUserId() { return userId; }
    
    public VectorClock getClientClock() { 
        return clientClock; 
    }
}