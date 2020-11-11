package Node;

/**
 * Thread that handles periodic message sending.
 */
public class ActiveMessageThread extends Thread {

    private RaftNode node;

    public ActiveMessageThread(RaftNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        while (true) {
            NodeType type = node.getType();
            if (type.equals(NodeType.LEADER)) {
                // If we are leader, we send heartbeat to each Peer every 2 seconds
                node.sendHeartbeat();
            }

            try {
                sleep(2000);
            } catch (InterruptedException ignored) { }
        }
    }
}
