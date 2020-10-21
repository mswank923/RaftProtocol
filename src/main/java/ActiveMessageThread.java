import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

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
        while (true) {
            NodeType type = node.getType();
            if (type.equals(NodeType.LEADER)) {
                // If we are leader, we send heartbeat to each Peer every 2 seconds
                node.sendHeartbeat();
                try {
                    sleep(2000);
                } catch (InterruptedException ignored) { }
            } else if (type.equals(NodeType.CANDIDATE)) {
                // If we are candidate, send vote request to each Peer
                node.requestVotes();
            }
        }
    }
}
