import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

/**
 * This class represents a node within the raft protocol
 */
public class RaftNode {

    static final int BROADCAST_PORT = 6788;
    static final int MESSAGE_PORT = 6789;

    // Election timeout range (in seconds)
    static final int ELECTION_TIMEOUT_MIN = 4;
    static final int ELECTION_TIMEOUT_MAX = 8;

    /**
     * Attributes
     */
    private int term;                       // The current term we are in

    private NodeType type;                  // The type of node this is
    private ArrayList<PeerNode> peerNodes;  // List of other nodes in the protocol
    private PeerNode myLeader;                      // Who is this node's leader
    private InetAddress address;            // The address of this node

    private boolean hasVoted;               // Has this node already voted (for leader election)
    private int voteCount;                  // How many votes does this node have (used for candidate nodes)

    private Date lastLeaderUpdate;          // The last time we heard from leader or a candidate
    private int electionTimeout;            // Timeout in milliseconds

    /**
     * Constructor for the local node that sets its initial values.
     */
    private RaftNode() {
        setType(NodeType.FOLLOWER);

        this.term = 0;
        this.voteCount = 0;

        this.hasVoted = false;

        this.lastLeaderUpdate = new Date();
        randomizeElectionTimeout();

        this.myLeader = null;
        this.peerNodes = new ArrayList<>();
        try {
            this.address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    private void setType(NodeType type) { this.type = type; }

    private void incrementVoteCount() {
        this.voteCount++;
    }

    NodeType getType() {
        return type;
    }

    InetAddress getAddress() { return this.address; }

    synchronized int getPeerCount() { return this.peerNodes.size(); }

    /**
     * Fetch a PeerNode based on an address.
     * @param address Address of the peer.
     * @return The PeerNode or null if not found.
     */
    private synchronized PeerNode getPeer(String address) {
        for (PeerNode peer : peerNodes)
            if (peer.equals(address))
                return peer;
        return null;
    }

    /**
     * Reset the last updated to current time for election timeout
     * and heartbeat timeout
     */
    private void resetTimeout(){
        this.lastLeaderUpdate = new Date();
    }

    /**
     * Generate a new random election timeout in the set range.
     */
    private void randomizeElectionTimeout() {
        System.out.println("Randomizing election timeout.");
        Random random = new Random();

        // Convert range to milliseconds
        int max = 1000 * ELECTION_TIMEOUT_MAX;
        int min = 1000 * ELECTION_TIMEOUT_MIN;

        electionTimeout = random.nextInt(max - min + 1) + min;
    }

    /**
     * Adds a newly created PeerNode to our list of Peers. Make sure to check that the peer does
     * not exist yet (getPeer != null).
     * @param peer The new peer to add.
     */
    synchronized void addNewPeer(PeerNode peer) {
        String name = peer.getAddress().getCanonicalHostName().split("\\.")[0];
        peerNodes.add(peer);
        log("Discovered peer " + name + ".");
    }

    private void incrementTerm() {
        term++;
    }

//    private void newLeader() {
//        randomizeElectionTimeout();
//        hasVoted = false;
//        resetTimeout();
//    }

    /**
     * Send a heartbeat message to all of our peers.
     */
    synchronized void sendHeartbeat() {
        log("Sending heartbeat.");
        Message message = new Message(MessageType.APPEND_ENTRIES, null);

        ArrayList<PeerNode> deadNodes = new ArrayList<>();
        for (PeerNode peer : peerNodes)
            if(!sendMessage(peer, message))
                deadNodes.add(peer);
        for (PeerNode deadPeer : deadNodes)
            peerNodes.remove(deadPeer);
    }

    /**
     * As candidate, send vote request message to peers
     */
    private synchronized void requestVotes() {
        log("Requesting votes.");
        Message message = new Message(MessageType.VOTE_REQUEST, this.term);

        ArrayList<PeerNode> deadNodes = new ArrayList<>();
        for (PeerNode peer : peerNodes)
            if(!sendMessage(peer, message))
                deadNodes.add(peer);
        for (PeerNode deadPeer : deadNodes)
            peerNodes.remove(deadPeer);
    }

    /**
     * Check to see if node doesn't have a leader or
     * leader has stopped sending append entries
     * @return true if doesn't have leader, false otherwise
     */
    private boolean leaderIsMissing() {
        long now = new Date().getTime();
        return now - lastLeaderUpdate.getTime() > electionTimeout;
    }

    /**
     * Check to see if candidate node has majority votes
     * @return true if has majority votes, false otherwise
     */
    private boolean checkMajority() {
        int majority = getPeerCount() / 2 + 1;
        return this.voteCount >= majority;
    }

    /**
     * Check to see if the vote is stuck in a tie.
     * @return true if we have gotten a VOTE_RESPONSE from every peer.
     */
    private synchronized boolean checkTieVote() {
        for (PeerNode peer : peerNodes)
            if (!peer.hasVoted())
                return false;
        return true;
    }

    /**
     * Start of leader election phase
     * Change this node to candidate, increment term, and reset everyone's votes
     */
    private synchronized void leaderElection() {
        setType(NodeType.CANDIDATE);
        incrementTerm();

        // Set all peer hasVoted attributes to false
        for (PeerNode peer : peerNodes)
            peer.resetVote();

        // Vote for ourselves
        incrementVoteCount();
        hasVoted = true;

        requestVotes();
    }

    /**
     * Open a socket, and send a message. Returns success status.
     * @param peer Destination Peer.
     * @param message Message to send.
     * @return Whether successful or not. false indicates a dead node.
     */
    private boolean sendMessage(PeerNode peer, Message message) {
        // Note: Only MessageTypes supported to send here are APPEND_ENTRIES and VOTE_REQUEST
        Socket socket = new Socket();
        try {
            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(peer.getAddress(), MESSAGE_PORT);
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
            processMessage(response, senderAddress);
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | ClassNotFoundException ignored) {

        }
        finally {
            if (!socket.isClosed())
                try { socket.close(); } catch (IOException ignored) { }
        }
        return true;
    }

    /**
     * Process a received Message object, and return a response if appropriate.
     * @param message The Message that was received.
     * @param sourceAddress The address of the source (sender) of the message.
     * @return A Message representing a response to the input Message, only if required by protocol.
     */
    Message processMessage(Message message, String sourceAddress) {
        PeerNode sourcePeer = getPeer(sourceAddress);
        if (sourcePeer == null)
            throw new RuntimeException("Received message from unknown peer!");

        MessageType type = message.getType();
        Object data = message.getData();

        switch (type) {
            case VOTE_REQUEST:
                // Type check
                if (!(data instanceof Integer))
                    throw new RuntimeException("Wrong data type for VOTE_REQUEST!");

                resetTimeout();

                int peerTerm = (int) data;

                // Determine response
                if (peerTerm > term || !hasVoted) {
                    hasVoted = true;
                    term = peerTerm;
                    log("Voted!");
                    return new Message(MessageType.VOTE_RESPONSE, true);
                } else { // we voted for someone else already
                    return new Message(MessageType.VOTE_RESPONSE, false);
                }

            case VOTE_RESPONSE:
                // Type check
                if (!(data instanceof Boolean))
                    throw new RuntimeException("Wrong data type for VOTE_RESPONSE!");

                log("Received vote.");

                // Did we get the vote?
                if ((boolean) data)
                    incrementVoteCount();

                // Update voted status for the peer
                sourcePeer.voted();

                break;

            case APPEND_ENTRIES:
                if (data == null) { // null indicates this was just a heartbeat

                    if (sourcePeer.equals(myLeader)) { // From current leader
                        log("Heard heartbeat.");
                    } else { // From new leader (indicates new term)
                        log("New leader!");
                        myLeader = sourcePeer;
                        hasVoted = false;
                        randomizeElectionTimeout();
                    }
                    resetTimeout();
                    return new Message(MessageType.APPEND_ENTRIES_RESPONSE, null);
                }
                // else if (data instanceof Entry) {
                else {
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES!");
                }

            case APPEND_ENTRIES_RESPONSE:
                break;

        }
        // If no response is called for, return null
        return null;
    }

    /**
     * Outputs a log-worthy message to stdout after tagging it with the local node's NodeType.
     * @param message The message to send to log.
     */
    void log(String message) {
        System.out.println("[" + type.toString() + "] " + message);
    }

    public static void main(String[] args) {
        // Start up the local node
        RaftNode thisNode = new RaftNode();

        // Receive broadcasts
        BroadcastPassiveThread broadcastPassiveThread = new BroadcastPassiveThread(thisNode);
        broadcastPassiveThread.start();

        // Start broadcasting
        BroadcastActiveThread broadcastActiveThread = new BroadcastActiveThread(thisNode);
        broadcastActiveThread.start();

        // Receive messages
        PassiveMessageThread passiveMessageThread = new PassiveMessageThread(thisNode);
        passiveMessageThread.start();

        // Start messaging
        ActiveMessageThread activeMessageThread = new ActiveMessageThread(thisNode);
        activeMessageThread.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) { }

        // Main loop performs constant checking
        while (true) {
            if (thisNode.type.equals(NodeType.FOLLOWER)) {
                // Check for missing leader
                if (thisNode.leaderIsMissing()) // Begin a new leader election cycle
                    thisNode.leaderElection();

            } else if (thisNode.type.equals(NodeType.CANDIDATE)) {
                // Check for majority or tie vote
                if (thisNode.checkMajority()) {
                    thisNode.log("Elected!");

                    thisNode.setType(NodeType.LEADER);
                    thisNode.hasVoted = false;
                    thisNode.randomizeElectionTimeout();
                    thisNode.resetTimeout();
                } else if (thisNode.checkTieVote()) { // No majority but everyone has voted
                    thisNode.setType(NodeType.FOLLOWER);
                    thisNode.randomizeElectionTimeout();
                    thisNode.resetTimeout();
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
