import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Thread that handles accepting incoming messages and processing of data.
 */
public class PassiveMessageThread extends Thread {

    private RaftNode node;

    PassiveMessageThread(RaftNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        // Process incoming communications.
        // Note: only incoming messages are VOTE_REQUEST and APPEND_ENTRIES
        while (true) {
            // 1. Socket opens
            Message message;
            String senderAddress;
            try (
                ServerSocket listener = new ServerSocket(RaftNode.MESSAGE_PORT);
                Socket socket = listener.accept();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                // 2. Read message from input
                message = (Message) in.readObject();
                senderAddress = socket.getInetAddress().getHostAddress();

                // 3. Process message & prepare response
                Message response = node.processMessage(message, senderAddress);

                // 4. Write response
                out.writeObject(response);
                if (message.getType().equals(MessageType.VOTE_REQUEST))
                    node.log("Voted!");

                socket.shutdownOutput();

                // 5. Wait until socket is closed
                while (in.read() != -1) {
                    sleep(50);
                }

            } catch (IOException | ClassNotFoundException | InterruptedException ignored) { }
        }
    }
}
