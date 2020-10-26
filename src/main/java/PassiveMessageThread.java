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
                    case HEARTBEAT:
                        InetAddress leaderAddress = (InetAddress) data;
                        PeerNode sender = node.getPeer(leaderAddress.getHostAddress());
                        sender.update();
                        break;
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
                        //Do something
                        break;
                    case APPEND_ENTRIES_RESPONSE:
                        //Do something
                        break;
                }

                // TODO
            } catch (IOException | ClassNotFoundException e){ e.printStackTrace(); }
        }
    }
}
