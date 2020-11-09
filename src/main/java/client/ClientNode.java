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
        leaderAddress = InetAddress.getByName(peer);
        if (!sendMessage(message)) {
            peers.remove(0);
            findLeader();
        }
    }

    /**
     * Send a message to the leader
     * @param message
     * @return
     */
    public boolean sendMessage(Message message){
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

    private static void printHelp() {
        String help = "";
        System.out.println(help);
    }

    private static boolean assertTrue(boolean condition) {
        if (!condition)
            printHelp();
        return !condition;
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

            if (assertTrue(split.length == 2 || split.length == 3))
                continue;
            if (assertTrue(split[0] != null && split[1] != null))
                continue;

            String command = split[0].toLowerCase();
            String key = split[1];

            LogEntry entry;
            switch (command) {
                case "get":
                    if (assertTrue(split.length == 2))
                        continue;
                    entry = new LogEntry(LogOp.RETRIEVE, key, -1);
                    break;
                case "set":
                    if (assertTrue(split.length == 3))
                        continue;

                    int value;
                    try {
                        value = Integer.parseInt(split[2]);
                    } catch (NumberFormatException e) {
                        printHelp();
                        continue;
                    }

                    entry = new LogEntry(LogOp.UPDATE, key, value);
                    break;
                case "del":
                    if (assertTrue(split.length == 2))
                        continue;
                    entry = new LogEntry(LogOp.DELETE, key, -1);
                    break;
                default:
                    printHelp();
                    continue;
            }

            Message message = new Message(MessageType.APPEND_ENTRIES, entry);
            if (!thisNode.sendMessage(message)) { // if sending fails, find leader & try again
                try {
                    thisNode.findLeader();
                } catch (UnknownHostException e) { e.printStackTrace(); }
                thisNode.sendMessage(message);
            }
        }

    }
}
