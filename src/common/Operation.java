package common;

import java.io.Serializable;

public class Operation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String type; // "INSERT" o "DELETE"
    private final int position;
    private final String text; // Texto a insertar o texto dummy para longitud de borrado
    private final String owner;
    private final VectorClock vectorClock;

    public Operation(String type, int position, String text, String owner, VectorClock vectorClock) {
        this.type = type;
        this.position = position;
        this.text = text;
        this.owner = owner;
        this.vectorClock = vectorClock;
    }

    public String getType() { return type; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public String getOwner() { return owner; }
    public VectorClock getVectorClock() { return vectorClock; }
}