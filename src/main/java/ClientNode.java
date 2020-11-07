import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;

public class ClientNode {

    /* Attributes */
    private ArrayList<String> peers;

    /* Constructor */
    public ClientNode() {
        this.peers = new ArrayList<>();
    }

    /* Getters and Setters */
    public ArrayList<String> getPeers() {
        return this.peers;
    }

    public void addPeer(String address) {
        this.peers.add(address);
    }


    /* Methods */

    /**
     * Get the InetAddress of the Leader for this term
     * @return Address of leader
     */
    public InetAddress findLeader(){
        return null;
    }

    /**
     * Send a message to the leader
     * @param message
     * @return
     */
    public boolean sendMessage(InetAddress leaderAddress, Message message){
        // Note: Only MessageTypes supported to send here are APPEND_ENTRIES and VOTE_REQUEST
        Socket socket = new Socket();
        try {
            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(leaderAddress, RaftNode.MESSAGE_PORT);
            socket.connect(destination, 500);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write to output
            out.writeObject(message);
            socket.shutdownOutput();

            // 3. Await response (response = input.readObject())
            Message response = (Message) in.readObject();
            String senderAddress = socket.getInetAddress().getHostAddress();

            // 4. Close socket
            socket.close();
            processMessage(response);
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | ClassNotFoundException ignored) {

        } finally {
            if (!socket.isClosed())
                try { socket.close(); } catch (IOException ignored) { }
        }
        return true;
    }

    public void processMessage(Message message){
        System.out.println("Hello");

    }

}
