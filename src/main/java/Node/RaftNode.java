package Node;

import static java.lang.Thread.sleep;
import static misc.MessageType.*;

import java.io.File;
import java.io.FileNotFoundException;
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

/**
 * This class represents the local node within the raft protocol.
 */
public class RaftNode {

    /**
     * Port on which to broadcast UDP packets. Must be exposed local and remote.
     */
    public static final int BROADCAST_PORT = 6788;

    /**
     * TCP port on which heartbeat is sent & received.
     */
    public static final int HEARTBEAT_PORT = 6789;

    /**
     * TCP port on which other messages are sent & received.
     */
    public static final int MESSAGE_PORT = 6790;

    /**
     * Election timeout range (in seconds)
     */
    private static final int ELECTION_TIMEOUT_MIN = 5;
    private static final int ELECTION_TIMEOUT_MAX = 10;

    /**
     * Seconds before an election to begin a countdown. Set to positive value to enable countdown.
     */
    private static final int ELECTION_COUNTDOWN_START = 0;

    /**
     * Name of the cache file to store keys & values in.
     */
    private static final String CACHE_FILENAME = "cache.txt";

    /**
     * Current term.
     */
    private int term;

    /**
     * Current type of node.
     */
    private NodeType type;

    /**
     * List of peers in the network. Writes to peers here must be thread-safe.
     */
    private ArrayList<PeerNode> peerNodes;

    /**
     * Current leader node. Initially null and set to null if this node is the leader.
     */
    private PeerNode myLeader;

    /**
     * Local node's address.
     */
    private InetAddress myAddress;

    /**
     * Address of the last client interacted with.
     */
    private InetAddress clientAddress;

    /**
     * Whether this node has voted in the current term.
     */
    private boolean hasVoted;

    /**
     * Votes for this CANDIDATE in the current election.
     */
    private int voteCount;

    /**
     * Total number of vote responses received in the current term/
     */
    private int totalVotes;

    /**
     * Timestamp of last message from leader or candidate
     */
    private Date lastLeaderUpdate;

    /**
     * Current term's election timeout in milliseconds
     */
    private int electionTimeout;

    /**
     * For each index in the array, indicates whether we have printed this countdown value yet.
     * Reset inside of resetTimeout().
     */
    private boolean[] hasPrinted;

    /**
     * In-memory key-value store.
     */
    private HashMap<String, Integer> cache;

    /**
     * Thread-safe queue of entries to send on heartbeat.
     */
    private ConcurrentLinkedQueue<LogEntry> entries;

    /**
     * Number of responses to the last LogEntry received so far.
     */
    private int responseCount;

    /**
     * Constructor for the local node, sets initial values.
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

        try {
            this.myAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) { e.printStackTrace(); }

        this.hasPrinted = new boolean[ELECTION_COUNTDOWN_START];

        this.peerNodes = new ArrayList<>();
        this.cache = new HashMap<>();
        this.entries = new ConcurrentLinkedQueue<>();
    }

    /**
     * Set the type of the local node.
     * @param type The new type.
     */
    void setType(NodeType type) { this.type = type; }

    /**
     * Set the voted status of the local node.
     * @param hasVoted The new hasVoted status.
     */
    void setHasVoted(boolean hasVoted) { this.hasVoted = hasVoted; }

    /**
     * Update the election term number.
     * @param term The new term number.
     */
    void setTerm(int term) { this.term = term; }

    /**
     * Update who the LEADER is.
     * @param leader The new LEADER peer, or null if this node is LEADER.
     */
    void setMyLeader(PeerNode leader) { this.myLeader = leader; }

    /**
     * Update the address of the client last interacted with.
     * @param host Address of the client as a String.
     */
    void setClientAddress(String host) {
        try {
            this.clientAddress = InetAddress.getByName(host);
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    /**
     * Adds a newly created Node.PeerNode to our list of Peers. Assumes that the peer does not yet
     * exist.
     * @param peer The new peer to add.
     */
    synchronized void addNewPeer(PeerNode peer) {
        String name = peer.getAddress().getCanonicalHostName().split("\\.")[0];
        peerNodes.add(peer);
        log("Discovered peer " + name + ".");
        resetTimeout();
    }

    /**
     * Add an entry to the outgoing queue to be sent to the other peer nodes.
     * @param entry The LogEntry to send.
     */
    void enqueueLogEntry(LogEntry entry) { this.entries.add(entry); }

    /**
     * Add a vote to the current term's vote tally. Must count total votes to check for tie cases.
     */
    void countVote(boolean vote) {
        // Filter out lingering votes from printing
        if (type.equals(NodeType.LEADER))
            return;

        // Count the vote
        totalVotes++;
        if (vote) {
            voteCount++;
            log("Votes: " + voteCount);
        }
    }

    /**
     * Increment the number of APPEND_ENTRIES_RESPONSE's received in response to the last
     * APPEND_ENTRIES request.
     */
    synchronized void incrementResponseCount() { this.responseCount++; }

    /**
     * Move on to the next term number. Occurs when node promotes to CANDIDATE, or when hearing from
     * a CANDIDATE for the first time in a term.
     */
    private void incrementTerm() { this.term++; }

    /**
     * Get the current type of the local node.
     * @return The current node type.
     */
    NodeType getType() { return this.type; }

    /**
     * Get the current leader peer.
     * @return The current leader peer, or null if we are leader or just started.
     */
    PeerNode getMyLeader() { return this.myLeader; }

    /**
     * Get the current term number to keep track of elections.
     * @return The term number.
     */
    int getTerm() { return this.term; }

    /**
     * Retrieve the voted status of the local node for this term.
     * @return Votes status of the local node.
     */
    boolean getHasVoted() { return this.hasVoted; }

    /**
     * Get the address of the local node.
     * @return The local node's address.
     */
    InetAddress getMyAddress() { return this.myAddress; }

    /**
     * Get the address of the last client interacted with.
     * @return Address of the client node.
     */
    InetAddress getClientAddress() { return this.clientAddress; }

    /**
     * Gets the total number of known and alive nodes, including the local node itself.
     * @return The number of nodes in the network that are alive.
     */
    synchronized int getNodeCount() {
        int count = 0;
        for (PeerNode peer : peerNodes)
            if (peer.isAlive())
                count++;
        return count + 1; // include local node
    }

    /**
     * Fetch a peer from peerList based on its address (as a String). Returns null if the Peer is
     * not known.
     * @param address Address of the peer.
     * @return The peer, or null if not found.
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
        lastLeaderUpdate = new Date();
        hasPrinted = new boolean[ELECTION_COUNTDOWN_START];
    }

    /**
     * Reset the vote counts and vote statuses for the start of a new election.
     */
    void resetVotes() {
        voteCount = 0;
        totalVotes = 0;
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
    void sendHeartbeat() {

        LogEntry entry = entries.poll();
        if (entry == null) {
            log("Sending heartbeat.");
        } else {
            log("Sending entry.");
            responseCount = 0;
        }

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

    /**
     * Check if this node has received a majority of responses to the last APPEND_ENTRIES message.
     * If so, executes the committing process.
     * @param lastResponseMessage Message to send back to the client.
     */
    synchronized void checkResponseMajority(String lastResponseMessage) {
        int peerCount = getNodeCount() - 1;
        int majority = (peerCount / 2) + 1;

        if (responseCount >= majority) {
            // 1. Commit our cache
            log("Commit cache");
            commitCacheToFile();

            // 2. Send commit message to followers
            Message commit = new Message(COMMIT, null);
            for (PeerNode peer : peerNodes)
                sendMessage(peer.getAddress(), commit);

            // 3. Notify client that entry is complete
            Message clientResponse = new Message(APPEND_ENTRIES_RESPONSE, lastResponseMessage);
            sendMessage(clientAddress, clientResponse);

            responseCount = 0;
        }
    }

    /**
     * Check if this node has received enough votes to become elected. If elected, the node becomes
     * LEADER.
     */
    synchronized void checkElectionResult() {
        // Filter out lingering votes
        if (type.equals(NodeType.LEADER))
            return;

        // Check the numbers
        int nodeCount = getNodeCount();
        int majority = (nodeCount / 2) + 1;

        if (voteCount >= majority) {
            // This node was elected leader
            log("Elected!");
            setType(NodeType.LEADER);
            myLeader = null;
        } else if (totalVotes == nodeCount) {
            // Everyone has voted, we don't have majority. This means tie vote occurred
            setType(NodeType.FOLLOWER);
        } else
            return;

        hasVoted = false;
        randomizeElectionTimeout();
        resetTimeout();
    }

    /**
     * Start a leader election term. Change this node to candidate, increment term, and reset
     * all peers' votes.
     */
    private synchronized void leaderElection() {
        // Initiate the election
        setType(NodeType.CANDIDATE);
        incrementTerm();
        resetVotes();

        // Vote for ourselves
        countVote(true);
        hasVoted = true;

        // Request votes
        log("Requesting votes.");
        for (PeerNode peer : peerNodes) {
            if (!this.type.equals(NodeType.CANDIDATE)) // Check to see if our election was cancelled
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
        int port = message.getType().equals(APPEND_ENTRIES) ? HEARTBEAT_PORT : MESSAGE_PORT;

        try (Socket socket = new Socket()) {
            // 1. Socket opens
            InetSocketAddress destination = new InetSocketAddress(address, port);
            socket.connect(destination, 300);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 2. Write message to output
            out.writeUnshared(message);

            // 3. Wait until socket is closed (peer closes when it's done receiving the data)
            while (in.read() != -1) { sleep(10); }
        } catch (SocketTimeoutException e) { // Peer is dead (most likely the leader we stopped)
            log("Sending message: FAILURE");
            return false;
        } catch (IOException | InterruptedException ignored) { }
        log("Sending message: success");
        return true;
    }

    /**
     * Retrieve a value from the cache in local memory.
     * @param key The key associated with the desired value.
     * @return The desired value.
     * @throws NullPointerException if the key does not exist in the cache.
     */
    int retrieveFromCache(String key) throws NullPointerException { return cache.get(key); }

    /**
     * Delete a key & value from the cache.
     * @param key The key of the key-value pair to delete.
     * @return The value returned (not used)
     * @throws NullPointerException if remove() returns null which int cannot be set to.
     */
    int deleteFromCache(String key) throws NullPointerException { return cache.remove(key); }

    /**
     * Add a key-value pair to the cache.
     * @param key The key to add.
     * @param value The value to add.
     */
    void addToCache(String key, int value) { cache.put(key, value); }

    /**
     * Commit the cache in local memory to a file format for persistent data.
     */
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
            try (Scanner fileScanner = new Scanner(cacheFile)) {
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
     * Outputs a log message to stdout after tagging it with the local node's type.
     * @param message The message to send to log.
     */
    void log(String message) { System.out.println("[" + type.toString() + "] " + message); }

    /**
     * Main method, runs the local node's program. Starts by initializing the node and its
     * communication threads, then enters mainloop of checks.
     * @param args Command-line arguments, unused.
     */
    public static void main(String[] args) {
        // Start up the local node
        RaftNode thisNode = new RaftNode();

        // Check for existence of a cache file
        thisNode.loadCacheFromFile();

        // Start sending & receiving broadcast peer discovery signals
        new PassiveBroadcastThread(thisNode).start();
        new ActiveBroadcastThread(thisNode).start();

        // 2 seconds added delay before message checking so other nodes can be discovered
        try { sleep(1000); } catch (InterruptedException ignored) { }

        // Receive messages
        new PassiveMessageThread(thisNode, MESSAGE_PORT).start();
        new PassiveMessageThread(thisNode, HEARTBEAT_PORT).start();

        // Start sending heartbeats
        new ActiveMessageThread(thisNode).start();

        // 2 seconds added delay before election checking so other nodes can startup
        try { sleep(1000); } catch (InterruptedException ignored) { }

        // Main loop performs constant checking based on local node's type
        while (true) {
            if (thisNode.type.equals(NodeType.FOLLOWER) && thisNode.leaderIsMissing())
                    thisNode.leaderElection();

            // Added delay so as not to hog the synchronized methods
            try {
                sleep(100);
            } catch (InterruptedException ignored) { }
        }
    }
}
