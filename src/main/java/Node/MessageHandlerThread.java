package Node;
import static misc.MessageType.APPEND_ENTRIES_RESPONSE;
import static misc.MessageType.FIND_LEADER;

import java.net.InetAddress;
import java.net.UnknownHostException;

import misc.LogEntry;
import misc.LogOp;
import misc.Message;
import misc.MessageType;

public class MessageHandlerThread extends Thread {
    private RaftNode node;
    private Message message;
    private String senderAddress;

    MessageHandlerThread(RaftNode node, Message message, String senderAddress) {
        this.node = node;
        this.message = message;
        this.senderAddress = senderAddress;
    }

    private void rudOperations(LogEntry entry){
        LogOp operation = entry.getOp();
        String key = entry.getKey();
        int value = entry.getValue();

        String message;
        switch(operation){
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

                if (node.getType().equals(NodeType.LEADER)) {
                    node.enqueueLogEntry(entry);
                } else {
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

                if (node.getType().equals(NodeType.LEADER)) {
                    node.enqueueLogEntry(entry);
                } else {
                    Message deleteResponse = new Message(APPEND_ENTRIES_RESPONSE, message);
                    node.sendMessage(node.getMyLeader().getAddress(), deleteResponse);
                }
                break;
        }
    }

    /**
     * Process a received Message object, and send a response if appropriate.
     * @param message The Message that was received.
     * @param sourceAddress The address of the source (sender) of the message.
     */
    private void processMessage(Message message, String sourceAddress) {

        MessageType type = message.getType();
        Object data = message.getData();

        if (type.equals(FIND_LEADER)) {
            System.out.println("Asked for leader. Responding...");
            try {
                node.setClientAddress(InetAddress.getByName(sourceAddress));
                Message msg;
                if (node.getType().equals(NodeType.LEADER)) // ff I am the leader send my address
                    msg = new Message(FIND_LEADER, node.getMyAddress());
                else // else send my leader's address
                    msg = new Message(FIND_LEADER, node.getMyLeader().getAddress());

                node.sendMessage(node.getClientAddress(), msg);
            } catch (UnknownHostException e) { e.printStackTrace(); }
            return;
        }

        PeerNode sourcePeer;

        switch (type) {
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

                Message response;
                if (vote) {
                    node.setHasVoted(true);
                    node.setTerm(peerTerm);
                    node.log("Voted!");
                    response = new Message(MessageType.VOTE_RESPONSE, true);
                } else {
                    response = new Message(MessageType.VOTE_RESPONSE, false);
                }

                sourcePeer = node.getPeer(sourceAddress);
                if (sourcePeer != null && !sourcePeer.isAlive())
                    sourcePeer.alive();
                node.sendMessage(sourcePeer.getAddress(), response);
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

                sourcePeer.voted();

                node.checkElectionResult();
                break;

            case APPEND_ENTRIES:
                node.resetTimeout();
                if (data == null) { // null indicates this was just a heartbeat
                    sourcePeer = node.getPeer(sourceAddress);
                    if (sourcePeer != null && !sourcePeer.isAlive())
                        sourcePeer.alive();

                    if (sourcePeer.equals(node.getMyLeader())) { // From current leader
                        node.log("Heard heartbeat.");
                    } else { // From new leader (indicates new term)
                        node.log("New leader!");
                        node.setMyLeader(sourcePeer);
                        node.setHasVoted(false);
                        node.randomizeElectionTimeout();
                    }

                    // If we are a candidate we need to stop our election
                    if (!node.getType().equals(NodeType.FOLLOWER))
                        node.setType(NodeType.FOLLOWER);

                    Message nullResponse = new Message(APPEND_ENTRIES_RESPONSE, null);
                    //node.sendMessage(sourcePeer.getAddress(), nullResponse);
                } else if (data instanceof LogEntry) {
                    if (node.getType().equals(NodeType.LEADER))
                        try {
                            node.setClientAddress(InetAddress.getByName(sourceAddress));
                        } catch (UnknownHostException e) { e.printStackTrace(); }

                    LogEntry entry = (LogEntry) data;
                    node.log("Received LogEntry of type " + entry.getOp().toString());
                    rudOperations(entry);
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

                String msg = (String) data;
                node.log("Received response. Checking majority.");
                node.incrementResponseCount();
                node.checkResponseMajority(msg);
                break;

            case COMMIT:
                node.log("Committing cache to file");
                node.commitCacheToFile();
                break;
        }
    }

    @Override
    public void run() {
        if (message != null && senderAddress != null)
            processMessage(message, senderAddress);
    }
}
