package misc;

import java.io.Serializable;

/**
 * A class representing an entry from client.
 */
public class LogEntry implements Serializable {

    /**
     * The type of log operation we are doing (retrieve, update, delete).
     */
    private LogOp op;

    /**
     * The key to the key-value pair we are looking at.
     */
    private String key;

    /**
     * The value to the key-value pair we are looking at.
     */
    private int value;

    /**
     * Constructor. Initializes values.
     * @param op Type of operation.
     * @param key Key to the value.
     * @param value New value, or null if not used (retrieve or delete).
     */
    public LogEntry(LogOp op, String key, int value){
        this.op = op;
        this.key = key;
        this.value = value;
    }

    /**
     * Retrieve the type of operation represented by this LogEntry.
     * @return Type of operation.
     */
    public LogOp getOp() { return this.op; }

    /**
     * Retrieve the key of the key-value pair targeted by this LogEntry.
     * @return The targeted key.
     */
    public String getKey() { return this.key; }

    /**
     * Retrieve the new value enclosed in this LogEntry.
     * @return The new value.
     */
    public int getValue() { return this.value; }
}
