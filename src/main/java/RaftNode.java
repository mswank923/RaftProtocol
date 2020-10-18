import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class represents a node within the raft protocol
 */
public class RaftNode {

    public static final int BROADCAST_PORT = 6788;
    public static final int MESSAGE_PORT = 6789;

    /**
     * Attributes
     */
    private int term;                       // The current term we are in
    private NodeType type;                  // The type of node this is
    private int voteCount;                  // How many votes does this node have (used for candidate nodes)
    private boolean hasVoted;               // Has this node already voted (for leader election)
                                            // Note: Candidate's vote for themselves
    private RaftNode myLeader;              // Who is this node's leader
    private ArrayList<PeerNode> peerNodes;  // List of other nodes in the protocol
    private Date lastUpdated;               // The last time that this node was heard from
    private InetAddress address;            // The address of this node


    public InetAddress getAddress() { return this.getAddress(); }

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

    public static void main(String[] args) {
        RaftNode thisNode = new RaftNode();

        // Begin broadcasting
        BroadcastActiveThread broadcastActiveThread = new BroadcastActiveThread(thisNode);
        broadcastActiveThread.start();

        PassiveMessageThread passiveMessageThread = new PassiveMessageThread(thisNode);
        passiveMessageThread.start();

        ActiveMessageThread activeThread = new ActiveMessageThread(thisNode);
        activeThread.start();

        // Set type to follower
        thisNode.type = NodeType.FOLLOWER;

        // Main loop checks for heartbeat & initiates leader election
        while (true) {
            //
        }
    }
}
