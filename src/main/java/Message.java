import java.io.Serializable;

/**
 * A class representing a message from one node to another.
 */
class Message implements Serializable {

    /**
     * The type of message being communicated.
     */
    private MessageType type;

    /**
     * The data being sent along with the message. Each MessageType has its corresponding data.
     * The receiving entity should use the Message's type to determine how to cast the data to its
     * proper type.
     */
    private Object data;

    Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    MessageType getType() { return type; }

    Object getData() { return data; }
}
