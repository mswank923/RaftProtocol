import java.util.ArrayList;
import java.util.Date;

/**
 * This class represents a node within the raft protocol
 */
public class RaftNode {

    /**
     * Type of node
     */
    enum NodeType{
        FOLLOWER, CANDIDATE, LEADER
    }


    /**
     * Attributes
     */
    private int id;                         // How to distinguish between nodes
    private int term;                       // The current term we are in
    private NodeType type;                  // The type of node this is
    private int voteCount;                  // How many votes does this node have (used for candidate nodes)
    private boolean hasVoted;               // Has this node already voted (for leader election)
                                            // Note: Candidate's vote for themselves
    private RaftNode myLeader;              // Who is this node's leader
    private ArrayList<RaftNode> peerNodes;  // List of other nodes in the protocol
    private Date lastUpdated;               // The last time that this node was heard from

    public static void main(String[] args) {

    }
}
