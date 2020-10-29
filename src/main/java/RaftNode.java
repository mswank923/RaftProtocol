import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

/**
 * This class represents the local node within the raft protocol.
 */
public class RaftNode {

    /**
     * Port on which to broadcast UDP packets. Must be exposed local and remote.
     */
    static final int BROADCAST_PORT = 6788;

    /**
     * Port on which to perform TCP communications. Must be exposed local and remote.
     */
    static final int MESSAGE_PORT = 6789;

    /**
     * Election timeout range (in seconds)
     */
    static final int ELECTION_TIMEOUT_MIN = 4;
    static final int ELECTION_TIMEOUT_MAX = 8;

    /**
     * Seconds before an election to begin a countdown.
     */
    static final int ELECTION_COUNTDOWN_START = 0;

    /**
     * Attributes
     */

    private int term;                      // Current term
    private NodeType type;                 // Current type of node

    private ArrayList<PeerNode> peerNodes; // List of peers in the protocol, must be thread-safe
    private PeerNode myLeader;             // Current leader node
    private InetAddress myAddress;         // Local node's address

    private boolean hasVoted;              // Voted status in current term
    private int voteCount;                 // Vote tally in current term

    private Date lastLeaderUpdate;         // Timestamp of last message from leader or candidate
    private int electionTimeout;           // Current term's election timeout in milliseconds


    /**
     * Constructor for the local node, sets its initial values.
     */
    private RaftNode() {
        setType(NodeType.FOLLOWER);
        resetTimeout();
        randomizeElectionTimeout();

        this.term = 0;
        this.voteCount = 0;

        this.hasVoted = false;
        this.myLeader = null;

        this.peerNodes = new ArrayList<>();

        try {
            this.myAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    /**
     * Set the type of the local node.
     * @param type The new type.
     */
    private void setType(NodeType type) { this.type = type; }

    /**
     * Adds a newly created PeerNode to our list of Peers. Assumes that the peer does not yet exist.
     * @param peer The new peer to add.
     */
    synchronized void addNewPeer(PeerNode peer) {
        String name = peer.getAddress().getCanonicalHostName().split("\\.")[0];
        peerNodes.add(peer);
        log("Discovered peer " + name + ".");
        resetTimeout();
    }

    /**
     * Add a vote to the current term's vote tally. Nodes vote for themselves once, and retrieve the
     * rest from REQUEST_VOTE messages to other peers. A majority vote allows a CANDIDATE to promote
     * to LEADER.
     */
    private void incrementVoteCount() { this.voteCount++; }

    /**
     * Move on to the next term number. Occurs when node promotes to CANDIDATE, or when hearing from
     * a CANDIDATE for the first time in a term.
     */
    private void incrementTerm() { term++; }

    /**
     * Get the current type of the local node.
     * @return The current node type.
     */
    NodeType getType() { return type; }

    /**
     * Get the address of the local node.
     * @return The local node's address.
     */
    InetAddress getMyAddress() { return this.myAddress; }

    /**
     * Gets the total number of known nodes, including the local node itself.
     * @return The number of nodes.
     */
    synchronized int getNodeCount() {
        int count = 0;
        for (PeerNode peer : peerNodes)
            if (peer.isAlive())
                count++;
        log("Node count: " + (count + 1));
        return count + 1; // include local node
    }

    /**
     * Fetch a PeerNode from peerList based on its address. Returns null if the Peer is not known.
     * @param address Address of the peer.
     * @return The PeerNode, or null if not found.
     */
    private synchronized PeerNode getPeer(String address) {
        for (PeerNode peer : peerNodes)
            if (peer.equals(address))
                return peer;
        return null;
    }

    /**
     * Reset the time that a leader or candidate was last heard from.
     */
    private void resetTimeout() { this.lastLeaderUpdate = new Date(); }

    /**
     * Generate a new random election timeout in the set range.
     */
    private void randomizeElectionTimeout() {
        log("Randomizing election timeout.");
        Random random = new Random();

        // Convert range to milliseconds
        int max = 1000 * ELECTION_TIMEOUT_MAX;
        int min = 1000 * ELECTION_TIMEOUT_MIN;

        electionTimeout = random.nextInt(max - min + 1) + min;
    }

    /**
     * Send a heartbeat message to all peers.
     */
    synchronized void sendHeartbeat() {
        log("Sending heartbeat.");
        Message message = new Message(MessageType.APPEND_ENTRIES, null);

        for (PeerNode peer : peerNodes)
            if (!sendMessage(peer, message))
                peer.dead();
    }

    /**
     * Send a vote request to a peer.
     */
    private synchronized void requestVote(PeerNode peer) {
        Message message = new Message(MessageType.VOTE_REQUEST, this.term);

        if (peer.isAlive() && !sendMessage(peer, message))
            peer.dead();
    }

    /**
     * Check to see if leader has stopped sending heartbeats and print a countdown if the
     * appropriate time has been reached.
     * @return true if leader is missing, false otherwise.
     */
    private boolean leaderIsMissing() {
        long now = new Date().getTime();
        long delta = now - lastLeaderUpdate.getTime();
        int remainingSecs = (int) ((electionTimeout - delta) / 1000);
        if (remainingSecs > 0 && remainingSecs <= ELECTION_COUNTDOWN_START) {
            if (remainingSecs == 1)
                log("Beginning election in " + remainingSecs + " second.");
            else
                log("Beginning election in " + remainingSecs + " seconds.");
        }
        return now - lastLeaderUpdate.getTime() > electionTimeout;
    }

    /**
     * Check to see if candidate node has the majority vote.
     * @return true if has majority votes, false otherwise.
     */
    private boolean checkMajorityVote() {
        int majority = getNodeCount() / 2 + 1;
        return this.voteCount >= majority;
    }

    /**
     * Check to see if the vote is stuck in a tie.
     * @return true if we have gotten a VOTE_RESPONSE from every peer, false otherwise.
     */
    private synchronized boolean checkTieVote() {
        for (PeerNode peer : peerNodes)
            if (!peer.hasVoted())
                return false;
        return true;
    }

    /**
     * Start a leader election term. Change this node to candidate, increment term, and reset
     * all peers' votes.
     */
    private synchronized void leaderElection() {
        setType(NodeType.CANDIDATE);
        incrementTerm();

        // Set all peer hasVoted attributes to false
        for (PeerNode peer : peerNodes)
            peer.resetVote();

        // Vote for ourselves
        incrementVoteCount();
        hasVoted = true;

        // Request votes
        log("Requesting votes.");
        for (PeerNode peer : peerNodes) {
            requestVote(peer);
            // Check for majority vote, then tie vote
            if (checkMajorityVote()) {
                // This node was elected leader
                log("Elected!");
                setType(NodeType.LEADER);
                hasVoted = false;
                myLeader = null;
                randomizeElectionTimeout();
                resetTimeout();
                return;
            }
        }
        // Everyone has voted, we don't have majority. This means tie vote occurred
        setType(NodeType.FOLLOWER);
        hasVoted = false;
        randomizeElectionTimeout();
        resetTimeout();
    }

    /**
     * Open a socket and send a message. Returns success status.
     * @param peer Destination Peer.
     * @param message Message to send.
     * @return Whether successful or not. false indicates a dead node.
     */
    private boolean sendMessage(PeerNode peer, Message message) {
        // Note: Only MessageTypes supported to send here are APPEND_ENTRIES and VOTE_REQUEST
        Socket socket = new Socket();
        try {
            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(peer.getAddress(), MESSAGE_PORT);
            socket.connect(destination, 500);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write to output
            out.writeObject(message);
            socket.shutdownOutput();

            // 3. Await response (response = input.readObject())
            Message response = (Message) in.readObject();
            String senderAddress = socket.getInetAddress().getHostAddress();

            // 4. Close socket
            socket.close();
            processMessage(response, senderAddress);
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | ClassNotFoundException ignored) {

        } finally {
            if (!socket.isClosed())
                try { socket.close(); } catch (IOException ignored) { }
        }
        return true;
    }

    /**
     * Process a received Message object, and return a response if appropriate.
     * @param message The Message that was received.
     * @param sourceAddress The address of the source (sender) of the message.
     * @return A Message representing a response to the input Message, only if required by protocol.
     */
    Message processMessage(Message message, String sourceAddress) {
        PeerNode sourcePeer = getPeer(sourceAddress);
        if (sourcePeer == null)
            throw new RuntimeException("Received message from unknown peer!");
        else if (!sourcePeer.isAlive())
            sourcePeer.alive();

        MessageType type = message.getType();
        Object data = message.getData();

        switch (type) {
            case VOTE_REQUEST:
                // Type check
                if (!(data instanceof Integer))
                    throw new RuntimeException("Wrong data type for VOTE_REQUEST!");

                resetTimeout();

                int peerTerm = (int) data;

                // Determine response
                if (peerTerm > term || !hasVoted) {
                    hasVoted = true;
                    term = peerTerm;
                    log("Voted!");
                    return new Message(MessageType.VOTE_RESPONSE, true);
                } else { // we voted for someone else already
                    return new Message(MessageType.VOTE_RESPONSE, false);
                }

            case VOTE_RESPONSE:
                // Type check
                if (!(data instanceof Boolean))
                    throw new RuntimeException("Wrong data type for VOTE_RESPONSE!");

                log("Received vote.");

                // Did we get the vote?
                if ((boolean) data)
                    incrementVoteCount();

                // Update voted status for the peer
                sourcePeer.voted();

                break;

            case APPEND_ENTRIES:
                if (data == null) { // null indicates this was just a heartbeat

                    if (sourcePeer.equals(myLeader)) { // From current leader
                        log("Heard heartbeat.");
                    } else { // From new leader (indicates new term)
                        log("New leader!");
                        myLeader = sourcePeer;
                        hasVoted = false;
                        randomizeElectionTimeout();
                    }
                    resetTimeout();
                    return new Message(MessageType.APPEND_ENTRIES_RESPONSE, null);
                }
                // else if (data instanceof Entry) {
                else {
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES!");
                }

            case APPEND_ENTRIES_RESPONSE:
                break;

        }
        // If no response is called for, return null
        return null;
    }

    /**
     * Outputs a log message to stdout after tagging it with the local node's NodeType.
     * @param message The message to send to log.
     */
    void log(String message) { System.out.println("[" + type.toString() + "] " + message); }

    /**
     * Main method, runs the local node's program. Starts by initializing the node and its
     * communication threads, then enters mainloop of type-specific checks including missing
     * leader and majority vote.
     * @param args Command-line arguments, unused.
     */
    public static void main(String[] args) {
        // Start up the local node
        RaftNode thisNode = new RaftNode();

        // Receive broadcasts
        PassiveBroadcastThread passiveBroadcastThread = new PassiveBroadcastThread(thisNode);
        passiveBroadcastThread.start();

        // Start broadcasting
        ActiveBroadcastThread activeBroadcastThread = new ActiveBroadcastThread(thisNode);
        activeBroadcastThread.start();

        // 2 seconds added delay before message checking so other nodes can be discovered
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) { }

        // Receive messages
        PassiveMessageThread passiveMessageThread = new PassiveMessageThread(thisNode);
        passiveMessageThread.start();

        // Start messaging
        ActiveMessageThread activeMessageThread = new ActiveMessageThread(thisNode);
        activeMessageThread.start();

        // 2 seconds added delay before election checking so other nodes can startup
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) { }

        // Main loop performs constant checking based on local node's type
        while (true) {
            if (thisNode.type.equals(NodeType.FOLLOWER)) {
                // Check for missing leader, and begin a new leader election cycle if true
                if (thisNode.leaderIsMissing())
                    thisNode.leaderElection();

            }

            // Added delay so we don't hog the synchronized methods
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
