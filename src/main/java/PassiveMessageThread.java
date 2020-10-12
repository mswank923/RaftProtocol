/**
 * Thread that handles accepting incoming messages and processing of data.
 */
public class PassiveMessageThread extends Thread {

    private RaftNode node;

    public PassiveMessageThread(RaftNode node) {
        this.node = node;
    }

    @Override
    public void run() {

    }
}
