package Node;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A class representing a neighbor of the local node. A list of PeerNodes are stored by RaftNode.
 */
class PeerNode {

    /**
     * The address of the peer.
     */
    private InetAddress address;

    /**
     * Whether this peer is alive.
     */
    private boolean alive;

    /**
     * Whether or not this node is in consensus.
     */
    private boolean consensus;

    /**
     * Constructor. Initializes values.
     * @param address The address (as String) of the peer picked up from broadcast.
     */
    PeerNode(String address) {
        this.alive = true;
        this.consensus = true;
        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    /**
     * Indicate that this peer is known to be alive.
     */
    void alive() { this.alive = true; }

    /**
     * Indicate that this peer is known to be nonactive.
     */
    void dead() { this.alive = false; }

    /**
     * Retrieve whether this peer is in consensus with the others.
     * @return Consensus state.
     */
    boolean getConsensus() { return this.consensus; }

    /**
     * Set the state of consensus of this peer.
     * @param consensus The new state of consensus.
     */
    void setConsensus(boolean consensus) { this.consensus = consensus; }

    /**
     * Retrieve the address of this peer.
     * @return The address of this peer.
     */
    InetAddress getAddress() { return this.address; }

    /**
     * Retrieve the alive status of this peer.
     * @return The alive status of this peer.
     */
    boolean isAlive() { return alive; }

    /**
     * Test for a match of an address (as String) to this peer.
     * @param address The address to test.
     * @return Whether the address belongs to this peer.
     */
    boolean equals(String address) { return this.address.getHostAddress().equals(address); }
}
