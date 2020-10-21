import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * A PeerNode represents a neighbor of a RaftNode. A list of PeerNodes are stored by the RaftNode.
 */
public class PeerNode {

    //////////////////
    //  Attributes  //
    //////////////////

    private NodeType type;
    private boolean hasVoted;
    private Date lastUpdated;
    private InetAddress address;

    PeerNode(String address) {
        this.type = NodeType.FOLLOWER;
        this.hasVoted = false;
        this.lastUpdated = new Date();
        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    public void update() {
        this.lastUpdated = new Date();
    }

    public void update(NodeType newType) {
        this.type = newType;
        this.update();
    }

    public void resetVote() {
        this.hasVoted = false;
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

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public boolean addressEquals(String address) {
        return this.address.getHostAddress().equals(address);
    }
}
