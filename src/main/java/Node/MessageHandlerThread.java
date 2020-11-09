package Node;

public class MessageHandlerThread extends Thread {
    private RaftNode node;
    private Message message;
    private String senderAddress;

    MessageHandlerThread(RaftNode node, Message message, String senderAddress) {
        this.node = node;
        this.message = message;
        this.senderAddress = senderAddress;
    }

    @Override
    public void run() {
        if (message != null && senderAddress != null)
            node.processMessage(message, senderAddress);
    }
}
