package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;
import misc.*;
import Node.*;

import static java.lang.Thread.sleep;

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
        try (Socket socket = new Socket()) {

            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(leaderAddress, RaftNode.MESSAGE_PORT);
            socket.connect(destination, 300);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write message to output
            out.writeUnshared(message);

            // 3. Wait until socket is closed (peer closes when it's done receiving the data)
            while (in.read() != -1) {
                sleep(50);
            }
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | InterruptedException ignored) { }

        return true;
    }

    public void processMessage(Message message){
        System.out.println("Hello");

    }

}
