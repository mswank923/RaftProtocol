package Node;
import static misc.MessageType.APPEND_ENTRIES_RESPONSE;
import static misc.MessageType.FIND_LEADER;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    /**
     * Process a received Message object, and send a response if appropriate.
     * @param message The Message that was received.
     * @param sourceAddress The address of the source (sender) of the message.
     */
    private void processMessage(Message message, String sourceAddress) {

        MessageType type = message.getType();
        Object data = message.getData();

        // Check to see if we receive a message from a client.
        if (type.equals(FIND_LEADER)) {
            try {
                InetAddress clientAddress = InetAddress.getByName(sourceAddress);
                Message msg;
                if (node.getType().equals(NodeType.LEADER)) // ff I am the leader send my address
                    msg = new Message(FIND_LEADER, node.getMyAddress());
                else // else send my leader's address
                    msg = new Message(FIND_LEADER, node.getMyLeader().getAddress());

                node.sendMessage(clientAddress, msg);
            } catch (UnknownHostException e) { e.printStackTrace(); }
            return;
        }

        // Message is from another node
        PeerNode sourcePeer = node.getPeer(sourceAddress);
        if (sourcePeer == null)
            throw new RuntimeException("Received message from unknown peer!");
        else if (!sourcePeer.isAlive())
            sourcePeer.alive();

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

                node.sendMessage(sourcePeer.getAddress(), response);
                break;

            case VOTE_RESPONSE:
                // Type check
                if (!(data instanceof Boolean))
                    throw new RuntimeException("Wrong data type for VOTE_RESPONSE!");

                node.log("Received vote.");

                // Did we get the vote?
                if ((boolean) data)
                    node.incrementVoteCount();

                // Update voted status for the peer
                sourcePeer.voted();
                node.incrementTotalVotes();

                node.checkElectionResult();
                break;

            case APPEND_ENTRIES:
                if (data == null) { // null indicates this was just a heartbeat
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

                    node.resetTimeout();
                    Message nullResponse = new Message(APPEND_ENTRIES_RESPONSE, null);
                    node.sendMessage(sourcePeer.getAddress(), nullResponse);
                    break;
                }
                // else if (data instanceof Entry) {
                else {
                    throw new RuntimeException("Wrong data type for APPEND_ENTRIES!");
                }

            case APPEND_ENTRIES_RESPONSE:
                break;
        }
    }

    @Override
    public void run() {
        if (message != null && senderAddress != null)
            processMessage(message, senderAddress);
    }
}
