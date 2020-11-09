package misc;

import java.io.Serializable;

/**
 * A class representing a message from one node to another.
 */
public class Message implements Serializable {

    /**
     * The type of message being communicated.
     */
    private MessageType type;

    /**
     * The data being sent along with the message. Each misc.MessageType has its corresponding data.
     * The receiving entity should use the misc.Message's type to determine how to cast the data to its
     * proper type.
     */
    private Object data;

    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public MessageType getType() { return type; }

    public Object getData() { return data; }
}
