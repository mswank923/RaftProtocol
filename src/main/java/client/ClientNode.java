package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;
import misc.*;
import Node.*;

import static java.lang.Thread.sleep;

public class ClientNode {

    /* Attributes */
    private ArrayList<String> peers;
    private InetAddress leaderAddress;

    /* Constructor */
    public ClientNode() {
        this.peers = new ArrayList<>();
        this.leaderAddress = null;
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
    public void findLeader() throws UnknownHostException {
        // Send FIND_LEADER message to random peer
        Message message = new Message(MessageType.FIND_LEADER, null);
        String peer = peers.get(0);
        InetAddress address = InetAddress.getByName(peer);
        sendMessage(address, message);
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

    private void printHelp() {
        String help = "";
        System.out.println(help);
    }

    public static void main(String[] args) {
        ClientNode thisNode = new ClientNode();

        // Start receiving broadcasts
        PassiveClientBroadcastThread broadcastThread = new PassiveClientBroadcastThread(thisNode);
        broadcastThread.start();

        // Find the leader
        try {
            thisNode.findLeader();
        } catch (UnknownHostException e) { e.printStackTrace(); }

        // Start up CLI
        Scanner input = new Scanner(System.in);
        String line;
        while((line = input.nextLine()) != null) {
            // Process command
            String[] split = line.split("\\s");
            if (!(split.length == 2 || split.length == 3)) {
                thisNode.printHelp();
                continue;
            }

            String command = split[0].toLowerCase();
            String key = split[1];

            switch (command) {
                case "get":
                    break;
                case "set":
                    break;
                case "del":
                    break;
                default:
                    thisNode.printHelp();
                    break;
            }
        }

    }
}
