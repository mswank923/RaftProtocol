package client;

public class PassiveClientMessageThread extends Thread{

    private ClientNode node;

    public PassiveClientMessageThread(ClientNode node){
        this.node = node;
    }

    @Override
    public void run(){

    }
}
