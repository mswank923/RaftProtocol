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

    public void reply(ObjectOutputStream out, Message msgReceived){
        MessageType messageType = msgReceived.getType();
        Object data = msgReceived.getData();
        try {
            switch (messageType) {
                case HEARTBEAT:
                    //Do something
                    break;
                case VOTE_REQUEST:
                    if (data instanceof InetAddress) {
                        if (node.hasVoted()) {
                            out.writeObject(false);
                        }
                        else {
                            out.writeObject(true);
                            node.setHasVoted(true);
                        }
                    }
                    break;
                case VOTE_RESPONSE:
                    //Do something
                    break;
                case APPEND_ENTRIES:
                    //Do something
                    break;
                case APPEND_ENTRIES_RESPONSE:
                    //Do something
                    break;
            }
        } catch (IOException e){ e.printStackTrace(); }
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
                }

                // TODO
            } catch (IOException | ClassNotFoundException e){ e.printStackTrace(); }
        }
    }
}
