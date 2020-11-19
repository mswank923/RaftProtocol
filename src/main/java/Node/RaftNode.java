package Node;

import static java.lang.Thread.sleep;
import static misc.MessageType.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.*;

import misc.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

import misc.*;

/**
 * This class represents the local node within the raft protocol.
 */
public class RaftNode {

    /**
     * Port on which to broadcast UDP packets. Must be exposed local and remote.
     */
    public static final int BROADCAST_PORT = 6788;

    /**
     * Port on which to perform TCP communications. Must be exposed local and remote.
     */
    public static final int MESSAGE_PORT = 6789;

    /**
     * Election timeout range (in seconds)
     */
    static final int ELECTION_TIMEOUT_MIN = 4;
    static final int ELECTION_TIMEOUT_MAX = 6;

    /**
     * Seconds before an election to begin a countdown.
     */
    static final int ELECTION_COUNTDOWN_START = 0;

    /**
     * Name of the cache file to store keys & values in.
     */
    static final String CACHE_FILENAME = "cache.txt";

    /**
     * Attributes
     */

    private int term;                                       // Current term
    private NodeType type;                                  // Current type of node

    private ArrayList<PeerNode> peerNodes;                  // List of peers in the protocol, must be thread-safe
    private PeerNode myLeader;                              // Current leader node
    private InetAddress myAddress;                          // Local node's address
    private InetAddress clientAddress;                      // Client's address

    private boolean hasVoted;                               // Voted status in current term
    private int voteCount;                                  // Vote tally in current term
    private int totalVotes;                                 // Number of vote responses received in current term

    private Date lastLeaderUpdate;                          // Timestamp of last message from leader or candidate
    private int electionTimeout;                            // Current term's election timeout in milliseconds

    private boolean[] hasPrinted;                           // Whether we have printed countdown yet

    private HashMap<String, Integer> cache;                 // In-memory key-value store
    private ConcurrentLinkedQueue<LogEntry> entries;        // Entries to send on heartbeat
    private int responseCount;                              // Number of responses received before committing entry

    /**
     * Constructor for the local node, sets its initial values.
     */
    private RaftNode() {
        setType(NodeType.FOLLOWER);
        resetTimeout();
        randomizeElectionTimeout();

        this.term = 0;
        this.voteCount = 0;
        this.totalVotes = 0;
        this.responseCount = 0;

        this.hasVoted = false;
        this.myLeader = null;
        this.clientAddress = null;

        this.peerNodes = new ArrayList<>();

        try {
            this.myAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }

        this.hasPrinted = new boolean[ELECTION_COUNTDOWN_START];

        this.cache = new HashMap<>();
        this.entries = new ConcurrentLinkedQueue<>();
    }

    /**
     * Set the type of the local node.
     * @param type The new type.
     */
    void setType(NodeType type) { this.type = type; }

    void setHasVoted(boolean hasVoted) { this.hasVoted = hasVoted; }

    void setTerm(int term) { this.term = term; }

    void setMyLeader(PeerNode leader) {
        this.myLeader = leader;
    }

    void setClientAddress(InetAddress address) { this.clientAddress = address; }

    /**
     * Adds a newly created Node.PeerNode to our list of Peers. Assumes that the peer does not yet exist.
     * @param peer The new peer to add.
     */
    synchronized void addNewPeer(PeerNode peer) {
        String name = peer.getAddress().getCanonicalHostName().split("\\.")[0];
        peerNodes.add(peer);
        log("Discovered peer " + name + ".");
        resetTimeout();
    }

    void enqueue(LogEntry entry){
        this.entries.add(entry);
    }

    /**
     * Add a vote to the current term's vote tally. Nodes vote for themselves once, and retrieve the
     * rest from REQUEST_VOTE messages to other peers. A majority vote allows a CANDIDATE to promote
     * to LEADER.
     */
    void incrementVoteCount() { this.voteCount++; }

    void incrementTotalVotes() { this.totalVotes++; }

    void incrementResponseCount() {
        this.responseCount++;
    }

    /**
     * Move on to the next term number. Occurs when node promotes to CANDIDATE, or when hearing from
     * a CANDIDATE for the first time in a term.
     */
    private void incrementTerm() { term++; }

    /**
     * Get the current type of the local node.
     * @return The current node type.
     */
    NodeType getType() { return this.type; }

    PeerNode getMyLeader() { return this.myLeader; }

    int getTerm() { return this.term; }

    boolean getHasVoted() { return this.hasVoted; }

    /**
     * Get the address of the local node.
     * @return The local node's address.
     */
    InetAddress getMyAddress() { return this.myAddress; }

    InetAddress getClientAddress() {
        return this.clientAddress;
    }

    /**
     * Gets the total number of known nodes, including the local node itself.
     * @return The number of nodes.
     */
    synchronized int getNodeCount() {
        int count = 0;
        for (PeerNode peer : peerNodes)
            if (peer.isAlive())
                count++;
        return count + 1; // include local node
    }

    /**
     * Fetch a Node.PeerNode from peerList based on its address. Returns null if the Peer is not known.
     * @param address Address of the peer.
     * @return The Node.PeerNode, or null if not found.
     */
    synchronized PeerNode getPeer(String address) {
        for (PeerNode peer : peerNodes)
            if (peer.equals(address))
                return peer;
        return null;
    }

    /**
     * Reset the time that a leader or candidate was last heard from.
     */
    void resetTimeout() {
        this.lastLeaderUpdate = new Date();
        this.hasPrinted = new boolean[ELECTION_COUNTDOWN_START];
    }

    /**
     * Generate a new random election timeout in the set range.
     */
    void randomizeElectionTimeout() {
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
        LogEntry entry = entries.poll();
        if (entry != null)
            responseCount = 0;
        Message message = new Message(MessageType.APPEND_ENTRIES, entry);

        for (PeerNode peer : peerNodes)
            if (!sendMessage(peer.getAddress(), message) && peer.isAlive())
                peer.dead();
    }

    /**
     * Send a vote request to a peer.
     */
    private synchronized void requestVote(PeerNode peer) {
        Message message = new Message(MessageType.VOTE_REQUEST, this.term);

        if (!sendMessage(peer.getAddress(), message) && peer.isAlive())
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
        if (remainingSecs > 0 && remainingSecs <= ELECTION_COUNTDOWN_START && !hasPrinted[remainingSecs - 1]) {
            hasPrinted[remainingSecs - 1] = true;
            if (remainingSecs == 1)
                log("Beginning election in " + remainingSecs + " second.");
            else
                log("Beginning election in " + remainingSecs + " seconds.");
        }
        return now - lastLeaderUpdate.getTime() > electionTimeout;
    }

    void checkResponseMajority(String lastResponseMessage) {

        int peerCount = getNodeCount() - 1;
        int majority = (peerCount / 2) + 1;

        if (responseCount >= majority) {
            log("Commit cache");
            commitCacheToFile();
            Message commit = new Message(COMMIT, null);
            for (PeerNode peer : peerNodes) {                 // Send commit message to followers
                sendMessage(peer.getAddress(), commit);
            }

            // Notify client that entry is complete
            Message clientResponse = new Message(APPEND_ENTRIES_RESPONSE, lastResponseMessage);
            sendMessage(clientAddress, clientResponse);

            responseCount = 0;
        }
    }

    synchronized void checkElectionResult() {
        if (type.equals(NodeType.LEADER))
            return;

        int nodeCount = getNodeCount();
        int majority = (nodeCount / 2) + 1;

        // Check for majority vote
        if (voteCount >= majority) {
            // This node was elected leader
            log("Elected!");
            setType(NodeType.LEADER);
            hasVoted = false;
            myLeader = null;
            randomizeElectionTimeout();
            resetTimeout();
        } else if (totalVotes == nodeCount) {
            // Everyone has voted, we don't have majority. This means tie vote occurred
            setType(NodeType.FOLLOWER);
            hasVoted = false;
            randomizeElectionTimeout();
            resetTimeout();
        }
    }

    /**
     * Start a leader election term. Change this node to candidate, increment term, and reset
     * all peers' votes.
     */
    private synchronized void leaderElection() {
        setType(NodeType.CANDIDATE);
        incrementTerm();
        voteCount = 0;
        totalVotes = 0;

        // Set all peer hasVoted attributes to false
        for (PeerNode peer : peerNodes)
            peer.resetVote();

        // Vote for ourselves
        incrementVoteCount();
        incrementTotalVotes();
        hasVoted = true;

        // Request votes
        log("Requesting votes.");
        for (PeerNode peer : peerNodes) {
            // Check to see if our election was cancelled
            if (!this.type.equals(NodeType.CANDIDATE))
                return;

            requestVote(peer);
        }
    }

    /**
     * Open a socket and send a message. Returns success status.
     * @param address Destination address.
     * @param message Message to send.
     * @return Whether successful or not. false indicates a dead node.
     */
    boolean sendMessage(InetAddress address, Message message) {
        try (Socket socket = new Socket()) {

            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(address, MESSAGE_PORT);
            socket.connect(destination, 300);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write message to output
            out.writeUnshared(message);

            // 3. Wait until socket is closed (peer closes when it's done receiving the data)
            while (in.read() != -1) { sleep(10); }
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            return false;
        } catch (IOException | InterruptedException ignored) { }

        return true;
    }

    int retrieveFromCache(String key) { return cache.get(key); }

    void deleteFromCache(String key) { cache.remove(key); }

    void addToCache(String key, int value) { cache.put(key, value); }

    void commitCacheToFile() {
        File cacheFile = new File(CACHE_FILENAME);
        cacheFile.delete();

        try (PrintWriter out = new PrintWriter(CACHE_FILENAME)) {
            for (String key : cache.keySet())
                out.println(key + " " + cache.get(key));
        } catch (FileNotFoundException e) { e.printStackTrace(); }
    }

    /**
     * If cache file exists, load the cache from the file.
     */
    private void loadCacheFromFile() {
        File cacheFile = new File(CACHE_FILENAME);
        if (cacheFile.exists()) {
            try {
                Scanner fileScanner = new Scanner(cacheFile);
                while (fileScanner.hasNextLine()) {
                    String line = fileScanner.nextLine();
                    String[] split = line.split(" ");

                    String key = split[0];
                    int value = Integer.parseInt(split[1]);

                    cache.put(key, value);
                }
            } catch (FileNotFoundException e) { e.printStackTrace(); }
        }
    }

    /**
     * Outputs a log message to stdout after tagging it with the local node's Node.NodeType.
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

        // Check for existence of a cache file
        thisNode.loadCacheFromFile();

        // Receive broadcasts
        PassiveBroadcastThread passiveBroadcastThread = new PassiveBroadcastThread(thisNode);
        passiveBroadcastThread.start();

        // Start broadcasting
        ActiveBroadcastThread activeBroadcastThread = new ActiveBroadcastThread(thisNode);
        activeBroadcastThread.start();

        // 2 seconds added delay before message checking so other nodes can be discovered
        try {
            sleep(1000);
        } catch (InterruptedException ignored) { }

        // Receive messages
        PassiveMessageThread passiveMessageThread = new PassiveMessageThread(thisNode);
        passiveMessageThread.start();

        // Start messaging
        ActiveMessageThread activeMessageThread = new ActiveMessageThread(thisNode);
        activeMessageThread.start();

        // 2 seconds added delay before election checking so other nodes can startup
        try {
            sleep(1000);
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
                sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
