import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private NodeType type;
    private boolean hasVoted;
    private Date lastUpdated;
    private InetAddress address;

    // Constructor
    PeerNode (String address) {
        this.type = NodeType.FOLLOWER;
        this.lastUpdated = new Date();
        this.hasVoted = false;

        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    ///////////////////////////
    //  Getters and Setters  //
    ///////////////////////////

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
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

    public InetAddress getAddress() { return this.address; }

    public boolean addressEquals(String address) {
        return this.address.getHostAddress().equals(address);
    }
}
