import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Thread that receives broadcasted addresses to recognize new or existing peers.
 */
public class BroadcastPassiveThread extends Thread {

    /**
     * Size in bytes of the buffer to read incoming transmissions into.
     */
    private static final int BUFSIZE = 1024;

    private RaftNode node;

    public BroadcastPassiveThread(RaftNode node) {
        this.node = node;
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
        PeerNode peer = node.getPeer(message);
        if (peer == null) {
            PeerNode newPeer = new PeerNode(message);
            node.addNewPeer(newPeer);
        }
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
