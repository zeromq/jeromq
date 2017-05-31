package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Synchronized subscriber.
 */
public class syncsub {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  First, connect our subscriber socket
        Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.connect("tcp://localhost:5561");
        subscriber.subscribe("");

        //  Second, synchronize with publisher
        Socket syncClient = context.createSocket(SocketType.REQ);
        syncClient.connect("tcp://localhost:5562");

        //  - send a synchronization request
        syncClient.send("");

        //  - wait for synchronization reply
        syncClient.receive();

        //  Third, get our updates and report how many we got
        int numberOfUpdates = 0;
        while (true) {
            String string = subscriber.receiveString();
            if (string.equals("END")) {
                System.out.println("Ending...");
                break;
            }
            numberOfUpdates++;
        }
        System.out.println("Received " + numberOfUpdates + " updates.");

        context.terminate();
    }
}