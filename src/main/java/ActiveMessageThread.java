/**
 * Thread that handles initializing communication and message sending.
 */
public class ActiveMessageThread extends Thread {

    private RaftNode node;

    public ActiveMessageThread(RaftNode node) {
        this.node = node;
    }

    @Override
    public void run() {

    }
}
