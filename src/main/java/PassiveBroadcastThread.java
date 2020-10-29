import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * Thread that receives broadcasted addresses to recognize new or existing peers.
 */
public class PassiveBroadcastThread extends Thread {

    /**
     * Size in bytes of the buffer to read incoming transmissions into.
     */
    private static final int BUFSIZE = 1024;

    /**
     * Array of addresses already found, so that we do not have to keep using node.getPeer() and
     * blocking all the threads constantly.
     */
    private ArrayList<String> addresses;

    private RaftNode node;

    public PassiveBroadcastThread(RaftNode node) {
        this.node = node;
        this.addresses = new ArrayList<>();
        try {
            this.addresses.add(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    private String receive(DatagramSocket socket) {
        byte[] buffer = new byte[BUFSIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (IOException e) { e.printStackTrace(); }

        return new String(packet.getData());
    }

    private void process(String message) {
        // message is the IP address that belongs to a peer node (or this node)

        // Prevent our own address from going through
        try {
            if (!InetAddress.getByName(message).getCanonicalHostName().contains("."))
                return;
        } catch (UnknownHostException e) { e.printStackTrace(); }

        // Filter out known addresses
        for (String s : addresses)
            if (s.equals(message))
                return;
        // Peer is new, add it
        PeerNode newPeer = new PeerNode(message);
        node.addNewPeer(newPeer);
        addresses.add(message);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(RaftNode.BROADCAST_PORT)) {
            while (true) {
                String message = receive(socket);
                process(message);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
