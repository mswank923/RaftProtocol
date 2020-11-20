package Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import misc.*;

/**
 * Thread that handles accepting incoming messages and spawning of MessageHandlerThreads.
 */
public class PassiveMessageThread extends Thread {

    /**
     * Reference to the local node.
     */
    private RaftNode node;

    /**
     * Port to listen on.
     */
    private int port;

    /**
     * Constructor. Initializes values.
     * @param node Reference to the local node.
     * @param port Port to listen on.
     */
    PassiveMessageThread(RaftNode node, int port) {
        this.node = node;
        this.port = port;
    }

    /**
     * Method defining the life of the thread. Thread continuously opens a ServerSocket and reads
     * incoming messages, then spawns a handler thread for the message.
     */
    @Override
    public void run() {
        // Process incoming communications.
        while (true) {
            Message message = null;

            String senderAddress = null;
            // 1. Socket opens
            try (
                ServerSocket listener = new ServerSocket(port);
                Socket socket = listener.accept()
            ) {
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                // 2. Read message from input
                message = (Message) in.readUnshared();
                senderAddress = socket.getInetAddress().getHostAddress();

                in.close();
                out.close();
                // 3. Close socket
            } catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }

            // 4. Process message
            new MessageHandlerThread(node, message, senderAddress).start();
        }
    }
}
