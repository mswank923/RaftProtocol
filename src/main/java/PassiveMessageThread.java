import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

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
        while (true) {
            try (
                ServerSocket listener = new ServerSocket(node.GOSSIP_PORT);
                Socket socket = listener.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
            ) {
                // do stuff
            }
        }
    }
}
