package common;

import java.io.Serializable;

/**
 * Contiene qué se escribió, dónde, quién fue y el reloj.
 * Implementa Serializable para poder viajar por RMI.
 */
public class Operation implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private final String type;      // "INSERT" o "DELETE"
    private final String text;      // El texto a insertar o borrar
    private final int position;
    private final int userId;       // Quién hizo el cambio
    private final VectorClock vectorClock; // El reloj del cliente

    // Constructor principal
    public Operation(String type, String text, int position, int userId, VectorClock vectorClock) {
        this.type = type;
        this.text = text;
        this.position = position;
        this.userId = userId;
        this.vectorClock = vectorClock; 
    }

    // --- Getters ---

    public String getType() { return type; }
    public String getText() { return text; }
    public int getPosition() { return position; }
    
    // Alias para compatibilidad con Document.java
    public int getOwner() { return userId; } 
    public int getUserId() { return userId; }
    
    public VectorClock getVectorClock() { return vectorClock; }
    // Alias por si usas getClientClock en otro lado
    public VectorClock getClientClock() { return vectorClock; }

    @Override
    public String toString() {
        return "Op[" + type + " '" + text + "' @ " + position + " by User " + userId + "]";
    }
}