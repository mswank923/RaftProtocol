import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Thread that handles initializing communication and message sending.
 */
public class ActiveMessageThread extends Thread {

    private RaftNode node;

    public ActiveMessageThread(RaftNode node) {
        this.node = node;
    }

    private void sendTo(RaftNode peer, Message msg){
        try(Socket socket = new Socket()){
            InetSocketAddress destination = new InetSocketAddress(peer.getAddress(), node.MESSAGE_PORT);
            socket.connect(destination, 10000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(msg);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

    }
}
