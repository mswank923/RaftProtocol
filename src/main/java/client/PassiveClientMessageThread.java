package client;

import Node.RaftNode;
import misc.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class PassiveClientMessageThread extends Thread{

    private ClientNode node;

    public PassiveClientMessageThread(ClientNode node){
        this.node = node;
    }

    @Override
    public void run(){
        while(true) {
            Message message = null;
            // 1. Socket opens
            try (
                    ServerSocket listener = new ServerSocket(RaftNode.MESSAGE_PORT);
                    Socket socket = listener.accept();
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                // 2. Read message from input
                message = (Message) in.readUnshared();

                // 3. Close socket
            } catch (IOException | ClassNotFoundException ignored) { }

            if (message != null)
                node.processMessage(message);
        }
    }
}
