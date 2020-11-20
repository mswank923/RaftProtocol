package Node;

/**
 * Thread that handles periodic message sending.
 */
public class ActiveMessageThread extends Thread {

    /**
     * Reference to the local node.
     */
    private RaftNode node;

    /**
     * Constructor. Initializes values.
     * @param node Reference to the local node.
     */
    public ActiveMessageThread(RaftNode node) { this.node = node; }

    /**
     * Method that defines the life of the thread. Continuously sends heartbeat every 2 seconds if
     * we are the leader.
     */
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
