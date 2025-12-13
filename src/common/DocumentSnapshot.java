package common;

import java.io.Serializable;

public class DocumentSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String content;
    private final VectorClock clock;
    
    public DocumentSnapshot(String content, VectorClock clock) {
        this.content = content;
        this.clock = clock;
    }
    
    public String getContent() { return content; }
    public VectorClock getClock() { return clock; }
}