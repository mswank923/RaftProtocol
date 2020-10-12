import java.io.*;
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
                    ServerSocket listener = new ServerSocket(node.MESSAGE_PORT);
                    Socket socket = listener.accept();
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())
            ) {
                Message msg = (Message) input.readObject();
                //TODO
            }catch(IOException | ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }
}
