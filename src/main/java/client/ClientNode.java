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
import static misc.MessageType.APPEND_ENTRIES;

/**
 * A class representing a client that attaches to the network of the nodes for inputting data.
 */
public class ClientNode {

    /**
     * A list of peer addresses that the client knows about through broadcast pickups.
     */
    private ArrayList<String> peers;

    /**
     * The address of the current leader.
     */
    private InetAddress leaderAddress;

    /**
     * Constructor. Initializes values.
     */
    private ClientNode() {
        this.peers = new ArrayList<>();
        this.leaderAddress = null;
    }

    /**
     * Retrieve the list of peer addresses.
     * @return The list of peer addresses.
     */
    ArrayList<String> getPeers() { return this.peers; }

    /**
     * Add a peer to the list of known peers.
     * @param address The address of the peer.
     */
    void addPeer(String address) {
        System.out.println("Found new peer.");
        this.peers.add(address);
    }


    /**
     * Get the InetAddress of the LEADER for this term.
     */
    void findLeader() {
        try {
            while (peers.size() == 0) { sleep(100); }

            System.out.println("Finding leader...");

            if (leaderAddress == null)
                leaderAddress = InetAddress.getByName(peers.get(0));

            // Send message to the address
            Message findLeader = new Message(MessageType.FIND_LEADER, null);
            sendMessage(findLeader);
        } catch(IndexOutOfBoundsException | InterruptedException | UnknownHostException ignored) { }
    }

    /**
     * Send a message to the leader.
     * @param message The message to send.
     * @return Success status.
     */
    boolean sendMessage(Message message){
        try (Socket socket = new Socket()) {
            int port = message.getType().equals(APPEND_ENTRIES) ?
                RaftNode.HEARTBEAT_PORT : RaftNode.MESSAGE_PORT;

            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(leaderAddress, port);
            socket.connect(destination, 300);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write message to output
            out.writeUnshared(message);

            // 3. Wait until socket is closed (peer closes when it's done receiving the data)
            while (in.read() != -1) { sleep(10); }

        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | InterruptedException ignored) { }

        return true;
    }

    /**
     * Process a message received from a peer.
     * @param message The message received.
     */
    void processMessage(Message message){
        MessageType msgType = message.getType();
        Object data = message.getData();
        switch(msgType){
            case FIND_LEADER: // Receiving leader address
                if (!(data instanceof InetAddress))
                    throw new RuntimeException("Wrong data type for FIND_LEADER");
                leaderAddress = (InetAddress) data;
                System.out.println("Found the leader.");
                break;
            case APPEND_ENTRIES_RESPONSE: // Receiving entries response
                if (!(data instanceof String))
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES_RESPONSE");

                String response = (String) data;
                System.out.println(response);
                break;
        }
    }

    /**
     * Print help message for cases where CLI is not of proper form.
     */
    private static void printHelp() {
        String help = "Commands:\n" +
                "get <key>: request a value of a certain key\n" +
                "set <key> <value>: request to update/add a value for a key\n" +
                "del <key>: request to remove a key-value pair\n" +
                "q: to quit the program";
        System.out.println(help);
    }

    /**
     * Assert that CLI is of proper form.
     * If it is not, then print help statement
     * @param condition Condition to satisfy proper form.
     * @return Whether to terminate processing of the command.
     */
    private static boolean assertTrue(boolean condition) {
        if (!condition)
            printHelp();
        return !condition;
    }

    /**
     * Main method. Drives the client program.
     * @param args Command-line args, unused.
     */
    public static void main(String[] args) {
        ClientNode thisNode = new ClientNode();

        // Start receiving broadcasts & messages
        new PassiveClientBroadcastThread(thisNode).start();
        new PassiveClientMessageThread(thisNode).start();

        // Find the leader
        thisNode.findLeader();

        // Start up CLI
        Scanner input = new Scanner(System.in);
        String line;
        while ((line = input.nextLine()) != null) {
            if (line.equals("q")) {
                System.out.println("Shutting down.");
                System.exit(0);
            }

            // Process command
            String[] split = line.split("\\s");

            if (assertTrue(split.length == 2 || split.length == 3))
                continue;
            if (assertTrue(split[0] != null && split[1] != null))
                continue;

            String command = split[0].toLowerCase();
            String key = split[1];

            LogEntry entry;

            // Look through all of the cases of the command input
            switch (command) {
                case "get":                 //Create a retrieve entry
                    if (assertTrue(split.length == 2))
                        continue;
                    entry = new LogEntry(LogOp.RETRIEVE, key, -1);
                    break;
                case "set":                 //Create an update entry
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
                case "del":                 //Create a delete entry
                    if (assertTrue(split.length == 2))
                        continue;
                    entry = new LogEntry(LogOp.DELETE, key, -1);
                    break;
                default:                    //Incorrect command given, print help message
                    printHelp();
                    continue;
            }

            Message message = new Message(MessageType.APPEND_ENTRIES, entry);
            boolean success = thisNode.sendMessage(message);
            if (!success) { // if sending fails, find leader & try again
                System.out.println("Connection to leader failed, finding leader again...");
                thisNode.findLeader();
                thisNode.sendMessage(message);
            }
        }

    }
}
