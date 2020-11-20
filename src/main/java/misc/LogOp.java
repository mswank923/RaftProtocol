package misc;

/**
 * Enumeration of a CRUD operation. Defines the operation meant to be performed by a LogEntry.
 */
public enum LogOp {

    /**
     * Client requests to retrieve key-value pair from log.
     */
    RETRIEVE,

    /**
     * Client requests to update key-value pair to new value.
     */
    UPDATE,

    /**
     * Client requests to delete a key-value pair.
     */
    DELETE
}
