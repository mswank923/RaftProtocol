package Node;

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

    PassiveBroadcastThread(RaftNode node) {
        this.node = node;
        this.addresses = new ArrayList<>();
        try {
            this.addresses.add(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) { e.printStackTrace(); }
    }

    /**
     * Receive a packet from a socket
     * @param socket socket we are receiving from
     * @return a string with the packet data
     */
    private String receive(DatagramSocket socket) {
        byte[] buffer = new byte[BUFSIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (IOException e) { e.printStackTrace(); }

        return new String(packet.getData());
    }

    /**
     * Add broadcasted nodes to raftNode's peer list
     * @param message The address of a peer
     */
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
