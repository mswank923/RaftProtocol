package client;

import Node.RaftNode;
import misc.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Thread that accepts incoming connections and messages and processes them.
 */
public class PassiveClientMessageThread extends Thread{

    /**
     * Reference to the client.
     */
    private ClientNode node;

    /**
     * Constructor. Initializes values.
     * @param node Reference to the local node.
     */
    PassiveClientMessageThread(ClientNode node){ this.node = node; }

    /**
     * Method that defines the life of the thread. Continuously accepts incoming connections and
     * reads a message, then processes the message.
     */
    @Override
    public void run(){
        while (true) {
            Message message = null;
            // 1. Socket opens
            try (
                    ServerSocket listener = new ServerSocket(RaftNode.MESSAGE_PORT);
                    Socket socket = listener.accept();
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                // 2. Read message from input
                message = (Message) in.readUnshared();

                // 3. Close socket
            } catch (IOException | ClassNotFoundException ignored) { }

            if (message != null)
                node.processMessage(message);
        }
    }
}
