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
        System.out.println("Found new peer.");
        this.peers.add(address);
    }


    /* Methods */

    /**
     * Get the InetAddress of the Leader for this term
     */
    public void findLeader() throws UnknownHostException {
        try {
            while (peers.size() == 0) {
                sleep(100);
            }
            System.out.println("Finding leader...");

            if (leaderAddress == null)
                leaderAddress = InetAddress.getByName(peers.get(0));

            // Send message to the address
            Message findLeader = new Message(MessageType.FIND_LEADER, null);
            sendMessage(findLeader);

            // Set leader to response
        } catch(IndexOutOfBoundsException | InterruptedException ignored) { }
    }

    /**
     * Send a message to the leader
     * @param message
     * @return
     */
    public boolean sendMessage(Message message){
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
     * Process a message received from a peer
     * @param message The message received
     */
    public void processMessage(Message message){
        MessageType msgType = message.getType();
        Object data = message.getData();
        switch(msgType){
            case FIND_LEADER:                       //Process what to do when receiving leader address
                if (!(data instanceof InetAddress))
                    throw new RuntimeException("Wrong data type for FIND_LEADER");
                leaderAddress = (InetAddress) data;
                System.out.println("Found the leader.");
                break;
            case APPEND_ENTRIES_RESPONSE:           //Process what to do when receiving entries response
                if (!(data instanceof String))
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES_RESPONSE");

                String response = (String) data;
                System.out.println(response);
                break;
        }
    }

    /**
     * Print help message for cases where cli is not correct
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
     * @param condition Condition to satisfy proper form
     * @return true if condition is not met, false otherwise
     */
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

        PassiveClientMessageThread messageThread = new PassiveClientMessageThread(thisNode);
        messageThread.start();

        // Find the leader
        try {
            thisNode.findLeader();
        } catch (UnknownHostException e) { e.printStackTrace(); }


        // Start up CLI
        Scanner input = new Scanner(System.in);
        String line;
        while((line = input.nextLine()) != null) {
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
                try {
                    thisNode.findLeader();
                } catch (UnknownHostException e) { e.printStackTrace(); }
                thisNode.sendMessage(message);
            }
        }

    }
}
