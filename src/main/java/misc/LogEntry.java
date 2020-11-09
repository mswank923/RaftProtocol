package misc;

/**
 * This class is used as an entry for a message
 * and defines what to do with that entry
 */
public class LogEntry {

    /**
     * The type of log operation we are doing (Retrieve, update, delete)
     */
    private LogOp action;

    /**
     * The key to the key-value pair we are looking at
     */
    private String key;

    /**
     * The value to the key-value pair we are looking at
     */
    private int value;

    public LogEntry(LogOp action, String key, int value){
        this.action = action;
        this.key = key;
        this.value = value;
    }

    public void commit() {

    }
}
