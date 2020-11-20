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
     * The data being sent along with the message. Each MessageType has its corresponding data type.
     * The receiving entity should use the Message's type to determine how to cast the data to its
     * proper type.
     */
    private Object data;

    /**
     * Constructor. Initializes values.
     * @param type The type of message.
     * @param data The accompanying data.
     */
    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Retrieve this message's type.
     * @return This message's type.
     */
    public MessageType getType() { return type; }

    /**
     * Retrieve this message's data.
     * @return This message's data.
     */
    public Object getData() { return data; }
}
