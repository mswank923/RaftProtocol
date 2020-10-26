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
            try (
                    ServerSocket listener = new ServerSocket(node.MESSAGE_PORT);
                    Socket socket = listener.accept();
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())
            ) {
                Message message = (Message) input.readObject();
                MessageType type = message.getType();
                Object data = message.getData();

                switch(type) {
                    case VOTE_REQUEST:
                        if (data instanceof InetAddress) {
                            if (node.hasVoted()) {
                                Message msg = new Message(MessageType.VOTE_RESPONSE, false);
                                output.writeObject(msg);
                            }
                            else {
                                Message msg = new Message(MessageType.VOTE_RESPONSE, true);
                                output.writeObject(msg);
                                node.setHasVoted(true);
                            }
                            //Reset node's election timeout
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
                            String peerAddress = socket.getInetAddress().getHostAddress();
                            PeerNode peer = node.getPeer(peerAddress);
                            peer.setHasVoted(true);
                        }
                        break;
                    case APPEND_ENTRIES:
                        if (data == null) { // we received a heartbeat
                            String sourceAddress = socket.getInetAddress().getHostAddress();
                            PeerNode sourcePeer = node.getPeer(sourceAddress);

                            if (sourcePeer.equals(node.myLeader)) {
                                sourcePeer.update();
                            } else { // new leader was elected
                                node.myLeader = sourcePeer;
                                // TODO randomize node's electionTimeout
                                node.term++;
                            }
                        } else {
                            // Data is entries we need to append (Project part 2)
                        }

                        break;
                    case APPEND_ENTRIES_RESPONSE:
                        //Do something
                        break;
                }

                try {
                    sleep(100);
                } catch (InterruptedException ignored) { }
            } catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }
        }
    }
}
