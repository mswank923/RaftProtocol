import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Thread that handles accepting incoming messages and processing of data.
 */
public class PassiveMessageThread extends Thread {

    private RaftNode node;

    PassiveMessageThread(RaftNode node) {
        this.node = node;
    }


    @Override
    public void run() {
        while (true) {
            // Added delay
            try {
                sleep(100);
            } catch (InterruptedException ignored) { }

            // Open socket for just enough time to retrieve a message
            Message message = null;
            String senderAddress = null;
            try (
                    ServerSocket listener = new ServerSocket(RaftNode.MESSAGE_PORT);
                    Socket socket = listener.accept();
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
            ) {
                senderAddress = socket.getInetAddress().getHostAddress();
                message = (Message) input.readObject();
            } catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }

            if (message == null)
                continue;

            PeerNode sourcePeer = node.getPeer(senderAddress);
            MessageType type = message.getType();
            Object data = message.getData();

            switch(type) {
                case VOTE_REQUEST:
                    if (data instanceof InetAddress) {
                        if (node.hasVoted()) {
                            Message msg = new Message(MessageType.VOTE_RESPONSE, false);
                            node.sendMessage(sourcePeer, msg); // reopen the socket
                        }
                        else {
                            Message msg = new Message(MessageType.VOTE_RESPONSE, true);
                            node.sendMessage(sourcePeer, msg); // reopen the socket
                            node.setHasVoted(true);
                        }
                        System.out.println("Voted!");

                        // Reset node's election timeout
                        node.resetTimeout();
                    }
                    break;
                case VOTE_RESPONSE:
                    if (data instanceof Boolean) {
                        // Check to see if we got the vote
                        boolean votedForMe = (boolean) data;
                        if (votedForMe)
                            node.incrementVoteCount();

                        // Track that this peer has voted
                        sourcePeer.setHasVoted(true);

                        System.out.println("Received vote.");
                    }
                    break;
                case APPEND_ENTRIES:
                    if (data == null) { // we received a heartbeat
                        if (sourcePeer.equals(node.myLeader)) {
                            System.out.println("Heard heartbeat.");
                        } else { // new leader was elected
                            System.out.println("New leader!");
                            node.myLeader = sourcePeer;
                            node.electionTimeout = node.randomIntGenerator(5000, 7000);
                            node.term++;
                        }
                        node.resetTimeout();
                    } else {
                        // Data is entries we need to append (Project part 2)
                    }

                    break;
                case APPEND_ENTRIES_RESPONSE:
                    //Do something
                    break;
            }
        }
    }
}
