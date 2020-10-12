/**
 * Thread that periodically broadcasts this node's IP address.
 */
public class BroadcastActiveThread extends Thread {

    private RaftNode node;

    public BroadcastActiveThread(RaftNode node) {
        this.node = node;
    }

    @Override
    public void run() {

    }
}
