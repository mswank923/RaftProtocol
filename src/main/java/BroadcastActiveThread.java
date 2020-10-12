import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Thread that periodically broadcasts this node's IP address.
 */
public class BroadcastActiveThread extends Thread {

    /**
     * Period between broadcasts in seconds.
     */
    private static final int PERIOD = 1;

    private RaftNode node;

    public BroadcastActiveThread(RaftNode node) {
        this.node = node;
    }

    /**
     * Broadcast a message.
     * @param address Broadcast destination address
     * @param message Message to be sent.
     * @param socket Socket on which to send the message.
     */
    private void broadcast(InetAddress address, String message, DatagramSocket socket) {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, RaftNode.BROADCAST_PORT);
        try {
            socket.send(packet);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            InetAddress destination = InetAddress.getByName("255.255.255.255");
            String message = InetAddress.getLocalHost().getHostAddress();

            while (true) {
                broadcast(destination, message, socket);
                sleep(PERIOD * 1000);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) { }
    }
}
