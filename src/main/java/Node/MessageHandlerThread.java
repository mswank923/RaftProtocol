package Node;
import static misc.MessageType.APPEND_ENTRIES_RESPONSE;
import static misc.MessageType.FIND_LEADER;
import static misc.MessageType.VOTE_RESPONSE;

import java.net.InetAddress;

import java.util.HashMap;
import misc.LogEntry;
import misc.LogOp;
import misc.Message;
import misc.MessageType;

/**
 * Class that handles the processing of an individual message.
 */
public class MessageHandlerThread extends Thread {

    /**
     * Reference to the local node.
     */
    private RaftNode node;

    /**
     * Message that was received.
     */
    private Message message;

    /**
     * Address of the sender of the message.
     */
    private String senderAddress;

    /**
     * Constructor. Initializes values.
     * @param node The local node.
     * @param message The message received.
     * @param senderAddress The address of the sender.
     */
    MessageHandlerThread(RaftNode node, Message message, String senderAddress) {
        this.node = node;
        this.message = message;
        this.senderAddress = senderAddress;
    }

    /**
     * Process a received LogEntry as a RETRIEVE, UPDATE, or DELETE operation.
     * @param entry The entry to process.
     */
    private void processLogEntry(LogEntry entry){
        LogOp op = entry.getOp();
        String key = entry.getKey();
        int value = entry.getValue();

        String message = "";
        switch (op) {
            case RETRIEVE:
                try {
                    int retrievedValue = node.retrieveFromCache(key);      // Get value from cache
                    message = "Value of " + key + " is " + retrievedValue;
                } catch (NullPointerException e) {
                    message = "No value for " + key;
                }

                // Send the value back to the client
                Message retrieveResponse = new Message(APPEND_ENTRIES_RESPONSE, message);
                node.sendMessage(node.getClientAddress(), retrieveResponse);
                break;
            case UPDATE:                                      // Update key value pair
                node.addToCache(key, value);
                message = "Value of " + key + " updated to " + value;

                if (node.getType().equals(NodeType.LEADER)) { // Leader sends on the LogEntry
                    node.enqueueLogEntry(entry);
                } else { // Nonleader writes response to the leader
                    Message updateResponse = new Message(APPEND_ENTRIES_RESPONSE, message);
                    node.sendMessage(node.getMyLeader().getAddress(), updateResponse);
                }
                break;
            case DELETE: // Remove a key value pair
                try {
                    node.deleteFromCache(key);
                    message = "Key " + key + " deleted";
                } catch (NullPointerException e) {
                    message = "No value for " + key;
                }

                if (node.getType().equals(NodeType.LEADER)) { // Leader sends on the LogEntry
                    node.enqueueLogEntry(entry);
                } else { // Nonleader writes response to the leader
                    Message deleteResponse = new Message(APPEND_ENTRIES_RESPONSE, message);
                    node.sendMessage(node.getMyLeader().getAddress(), deleteResponse);
                }
                break;
        }

        node.log(message);
    }

    /**
     * Process a received Message object, and send a response if appropriate.
     * @param message The Message that was received.
     * @param sourceAddress The address of the source (sender) of the message.
     */
    private void processMessage(Message message, String sourceAddress) {
        MessageType type = message.getType();
        Object data = message.getData();

        PeerNode sourcePeer;
        Message responseMessage;

        switch (type) {
            case FIND_LEADER:
                node.log("Found client.");
                node.setClientAddress(sourceAddress);

                // Find the leader
                InetAddress leaderAddress = node.getType().equals(NodeType.LEADER) ?
                    node.getMyAddress() : node.getMyLeader().getAddress();

                // Tell the client
                responseMessage = new Message(FIND_LEADER, leaderAddress);
                node.sendMessage(node.getClientAddress(), responseMessage);

                break;
            case VOTE_REQUEST:
                if (!(data instanceof Integer))
                    throw new RuntimeException("Wrong data type for VOTE_REQUEST!");

                node.resetTimeout();

                int peerTerm = (int) data;

                // Determine response
                boolean vote = false;
                if (node.getType().equals(NodeType.FOLLOWER)) {
                    if (peerTerm > node.getTerm())
                        vote = true;
                    else // peerTerm == term
                        vote = !node.getHasVoted();
                }

                if (vote) {
                    node.setHasVoted(true);
                    node.setTerm(peerTerm);
                    node.log("Voted!");
                }

                responseMessage = new Message(VOTE_RESPONSE, vote);

                sourcePeer = node.getPeer(sourceAddress);
                if (sourcePeer != null && !sourcePeer.isAlive())
                    sourcePeer.alive();

                if (sourcePeer == null)
                    throw new RuntimeException("Received VOTE_REQUEST from unknown peer!");
                else
                    node.sendMessage(sourcePeer.getAddress(), responseMessage);
                break;

            case VOTE_RESPONSE:
                if (node.getType().equals(NodeType.LEADER))
                    break;

                // Type check
                if (!(data instanceof Boolean))
                    throw new RuntimeException("Wrong data type for VOTE_RESPONSE!");

                // Did we get the vote?
                node.countVote((boolean) data);

                // Update voted status for the peer
                sourcePeer = node.getPeer(sourceAddress);
                if (sourcePeer != null && !sourcePeer.isAlive())
                    sourcePeer.alive();

                node.checkElectionResult();
                break;

            case APPEND_ENTRIES:
                node.resetTimeout();

                sourcePeer = node.getPeer(sourceAddress);
                if (sourcePeer != null) {
                    if (!sourcePeer.isAlive())
                        sourcePeer.alive();

                    if (!sourcePeer.equals(node.getMyLeader())) { // From new leader
                        node.log("New leader!");
                        node.setMyLeader(sourcePeer);
                        node.setHasVoted(false);
                        node.randomizeElectionTimeout();
                    }
                }

                // Process the data
                if (data == null) { // A heartbeat from the leader
                    node.log("Heard heartbeat.");

                    if (!node.getType().equals(NodeType.FOLLOWER))
                        node.setType(NodeType.FOLLOWER);

                    // null response is not necessary
//                    responseMessage = new Message(APPEND_ENTRIES_RESPONSE, null);
//                    if (sourcePeer == null)
//                        throw new RuntimeException("Received heartbeat from unknown peer!");
//                    else
//                        node.sendMessage(sourcePeer.getAddress(), responseMessage);

                } else if (data instanceof LogEntry) {
                    node.setClientAddress(sourceAddress);

                    LogEntry entry = (LogEntry) data;
                    processLogEntry(entry);
                } else if (data instanceof HashMap) { // New cache from leader
                    node.log("Updating cache with data from leader.");

                    @SuppressWarnings("unchecked") // Suppress unchecked cast warning
                    HashMap<String, Integer> newCache = (HashMap<String, Integer>) data;

                    node.replaceCache(newCache);
                    node.commitCacheToFile();
                } else {
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES!");
                }
                break;

            case APPEND_ENTRIES_RESPONSE: // Only received by LEADER
                if (data == null)
                    break;

                // Type check
                if (!(data instanceof String))
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES_RESPONSE!");

                String text = (String) data;
                node.log("Received response.");
                node.incrementResponseCount();
                node.checkResponseMajority(text);
                break;

            case COMMIT:
                node.log("Committing cache to file");
                node.commitCacheToFile();
                break;
        }
    }

    /**
     * Method defining the lifetime of the thread. The thread processes a single message and then
     * concludes itself.
     */
    @Override
    public void run() {
        if (message != null && senderAddress != null)
            processMessage(message, senderAddress);
    }
}
