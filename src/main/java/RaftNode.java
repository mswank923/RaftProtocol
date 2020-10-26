import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class represents a node within the raft protocol
 */
public class RaftNode {

    static final int BROADCAST_PORT = 6788;
    static final int MESSAGE_PORT = 6789;

    /**
     * Attributes
     */
    int term;                       // The current term we are in

    private NodeType type;                  // The type of node this is
    private ArrayList<PeerNode> peerNodes;  // List of other nodes in the protocol
    PeerNode myLeader;              // Who is this node's leader
    private InetAddress address;            // The address of this node

    private boolean hasVoted;               // Has this node already voted (for leader election)
    private int voteCount;                  // How many votes does this node have (used for candidate nodes)

    private Date lastUpdated;
    private int electionTimeout;            // Timeout in milliseconds

    /**
     * Constructor for the local node that sets its initial values.
     */
    private RaftNode() {
        this.term = 0;
        this.type = NodeType.FOLLOWER;
        this.voteCount = 0;
        this.hasVoted = false;
        this.lastUpdated = new Date();

        this.myLeader = null;
        this.peerNodes = new ArrayList<PeerNode>();
        try {
            this.address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    //////////////////////////
    //  Getters and Setters //
    //////////////////////////

    public NodeType getType() {
        return type;
    }

    InetAddress getAddress() { return this.address; }

    int getVoteCount() { return this.voteCount; }

    synchronized int getPeerCount() { return this.peerNodes.size(); }

    void setType(NodeType type) { this.type = type; }

    boolean hasVoted(){
        return this.hasVoted;
    }

    void setHasVoted(boolean voted){
        this.hasVoted = voted;
    }

    void incrementVoteCount() {
        this.voteCount++;
    }

    /**
     * Reset the last updated to current time for election timeout
     * and heartbeat timeout
     */
    void resetTimeout(){
        this.lastUpdated = new Date();
    }

    /**
     * Fetch a PeerNode based on an address.
     * @param address Address of the peer.
     * @return The PeerNode or null if not found.
     */
    synchronized PeerNode getPeer(String address) {
        for (PeerNode peer : peerNodes)
            if (peer.addressEquals(address))
                return peer;
        return null;
    }

    /**
     * Adds a newly created PeerNode to our list of Peers. Make sure to check that the peer does
     * not exist yet (getPeer != null).
     * @param peer The new peer to add.
     */
    synchronized void addNewPeer(PeerNode peer) {
        peerNodes.add(peer);
    }

    /**
     * Send a heartbeat message to all of our peers.
     */
    synchronized void sendHeartbeat() {
        Message message = new Message(MessageType.APPEND_ENTRIES, null);
        for (PeerNode peer : peerNodes)
            sendMessage(peer, message);
    }

    /**
     * As candidate, send vote request message to peers
     */
    synchronized void requestVotes() {
        Message message = new Message(MessageType.VOTE_REQUEST, this.address);
        //Update timeout
        this.lastUpdated = new Date();
        for (PeerNode peer : peerNodes)
            if (!peer.hasVoted())
                sendMessage(peer, message);
    }

    /**
     * Check to see if node doesn't have a leader or
     * leader has stopped sending append entries
     * @return true if doesn't have leader, false otherwise
     */
    boolean leaderIsMissing() {
        long now = new Date().getTime();

        // Base Case (When we first start raft protocol)
        if (myLeader == null || now > electionTimeout)
            return true;

        long lastUpdateTime = myLeader.getLastUpdated().getTime();
        return now - lastUpdateTime > electionTimeout;
    }

    /**
     * Check to see if candidate node has majority votes
     * @return true if has majority votes, false otherwise
     */
    boolean checkMajority() {
        int majority = this.peerNodes.size() / 2 + 1;
        return this.voteCount >= majority;
    }

    /**
     * Check to see if the vote is stuck in a tie.
     * @return true if we have gotten a VOTE_RESPONSE from every peer.
     */
    synchronized boolean checkTieVote() {
        for (PeerNode peer : peerNodes)
            if (!peer.hasVoted())
                return false;
        return true;
    }

    /**
     * Start of leader election phase
     * Change this node to candidate, increment term
     * and reset everyone's votes
     */
    synchronized void leaderElection() {
        this.type = NodeType.CANDIDATE;

        // Set all peer hasVoted attributes to false
        for (PeerNode peer : peerNodes)
            peer.resetVote();

        requestVotes();
        voteCount++;
    }

    /**
     * Open a socket and send the message to the peer.
     * @param peer Destination Peer.
     * @param message Message to send.
     */
    void sendMessage(PeerNode peer, Message message) {
        try (Socket socket = new Socket()) {
            InetSocketAddress destination = new InetSocketAddress(peer.getAddress(), MESSAGE_PORT);
            socket.connect(destination, 10000);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(message);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        RaftNode thisNode = new RaftNode();

        // Start broadcasting
        BroadcastActiveThread broadcastActiveThread = new BroadcastActiveThread(thisNode);
        broadcastActiveThread.start();

        // Receive broadcasts
        BroadcastPassiveThread broadcastPassiveThread = new BroadcastPassiveThread(thisNode);
        broadcastPassiveThread.start();

        // Receive messages
        PassiveMessageThread passiveMessageThread = new PassiveMessageThread(thisNode);
        passiveMessageThread.start();

        // Start messaging
        ActiveMessageThread activeMessageThread = new ActiveMessageThread(thisNode);
        activeMessageThread.start();


        // Main loop checks for heartbeat & initiates leader election
        while (true) {
            // Check for missing leader
            if (thisNode.type == NodeType.FOLLOWER && thisNode.leaderIsMissing()) {
                thisNode.leaderElection();
            } else if (thisNode.type == NodeType.CANDIDATE) {
                if (thisNode.checkMajority()) {
                    thisNode.type = NodeType.LEADER;
                    // TODO randomize electionTimeout
                    thisNode.term += 1;
                } else if (thisNode.checkTieVote()) {
                    thisNode.leaderElection();
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
