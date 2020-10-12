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

        /**
         * A Follower hears from a leader to receive information
         * If it does not hear from a Leader before it's timeout, then
         * it becomes a candidate for the leader election.
         * Followers reply to candidates with their vote
         */
        FOLLOWER,

        /**
         * Sends out request for a vote in leader election phase
         * (Note: It votes for itself)
         * Candidate tallies how many votes it received.
         * If it received majority votes, then it will become a leader
         * In the case where Candidates are tied in votes, then no one
         * becomes a leader, and the leader election starts over again
         */
        CANDIDATE,

        /**
         * Changes are sent to leader nodes
         * The Leader stores the uncommitted changes in its log
         * Then it replicates the changes to follower nodes
         * Leader waits for responses from majority of followers
         * Leader commits the change and notifies followers that
         * the change is committed, therefore consensus is reached
         */
        LEADER
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
        RaftNode thisNode = new RaftNode();

        PassiveMessageThread passiveThread = new PassiveMessageThread(thisNode);
        passiveThread.start();

        ActiveMessageThread activeThread = new ActiveMessageThread(thisNode);
        activeThread.start();
    }
}
