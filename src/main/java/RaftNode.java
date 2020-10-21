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
    private int term;                       // The current term we are in

    private NodeType type;                  // The type of node this is
    private int voteCount;                  // How many votes does this node have (used for candidate nodes)
    private boolean hasVoted;               // Has this node already voted (for leader election)
                                            // Note: Candidate's vote for themselves
    private PeerNode myLeader;              // Who is this node's leader
    private ArrayList<PeerNode> peerNodes;  // List of other nodes in the protocol
    private InetAddress address;            // The address of this node

    private Date lastElectionUpdate;

    /**
     * Constructor for the local node that sets its initial values.
     */
    private RaftNode() {
        this.term = 0;
        this.type = NodeType.FOLLOWER;
        this.voteCount = 0;
        this.hasVoted = false;

        this.myLeader = null;
        this.peerNodes = new ArrayList<>();
        try {
            this.address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }

        this.lastElectionUpdate = new Date();
    }

    public NodeType getType() {
        return type;
    }

    InetAddress getAddress() { return this.address; }

    boolean hasVoted(){
        return this.hasVoted;
    }

    void setHasVoted(boolean voted){
        this.hasVoted = voted;
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
        Message message = new Message(MessageType.HEARTBEAT, this.address);
        for (PeerNode peer : peerNodes)
            sendMessage(peer, message);
    }

    synchronized void requestVotes() {
        Message message = new Message(MessageType.VOTE_REQUEST, this.address);
        for (PeerNode peer : peerNodes)
            sendMessage(peer, message);
    }

    boolean leaderIsMissing() {
        long lastUpdateTime = myLeader.getLastUpdated().getTime();
        long now = new Date().getTime();
        return now - lastUpdateTime > 10000;
    }

    synchronized void leaderElection() {
        this.type = NodeType.CANDIDATE;
        this.term += 1;

        // Set all peer hasVoted attributes to false
        for (PeerNode peer : peerNodes)
            peer.resetVote();
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

        // Set type to follower
        thisNode.type = NodeType.FOLLOWER;

        // Main loop checks for heartbeat & initiates leader election
        while (true) {
            // Check for missing leader
            if (thisNode.type == NodeType.FOLLOWER && thisNode.leaderIsMissing()) {
                // elect a leader
                thisNode.leaderElection();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
