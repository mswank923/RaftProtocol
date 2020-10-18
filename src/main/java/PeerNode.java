import java.util.Date;

/**
 * A PeerNode represents a neighbor of a RaftNode
 * There is no need for the neighbors of a
 * raft node to have so many attributes to them.
 */
public class PeerNode {

    //////////////////
    //  Attributes  //
    //////////////////

    private int id;
    private RaftNode.NodeType type;
    private boolean hasVoted;
    private Date lastUpdated;

    ///////////////////////////
    //  Getters and Setters  //
    ///////////////////////////

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public RaftNode.NodeType getType() {
        return type;
    }

    public void setType(RaftNode.NodeType type) {
        this.type = type;
    }

    public boolean hasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
