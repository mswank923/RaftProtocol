import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A PeerNode represents a neighbor of a RaftNode. A list of PeerNodes are stored by the RaftNode.
 */
class PeerNode {

    private boolean hasVoted;
    private InetAddress address;
    private boolean alive;

    PeerNode(String address) {
        this.hasVoted = false;
        this.alive = true;
        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    void voted() {
        this.hasVoted = true;
    }

    void resetVote() {
        this.hasVoted = false;
    }

    void alive() { this.alive = true; }

    void dead() { this.alive = false; }

    InetAddress getAddress() {
        return address;
    }

    boolean hasVoted() {
        return hasVoted;
    }

    boolean isAlive() { return alive; }

    boolean equals(String address) { return this.address.getHostAddress().equals(address); }
}
