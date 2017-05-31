package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Hello World client
 * Connects REQ socket to tcp://localhost:5559
 * Sends "Hello" to server, expects "World" back
 */
public class rrclient {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to talk to server
        Socket requester = context.createSocket(SocketType.REQ);
        requester.connect("tcp://localhost:5559");

        System.out.println("launch and connect client.");

        for (int requestNumber = 0; requestNumber < 10; requestNumber++) {
            requester.send("Hello");
            String reply = requester.receiveString();
            System.out.println("Received reply " + requestNumber + " [" + reply + "]");
        }

        context.terminate();
    }
}