package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Pubsub envelope subscriber
 */
public class psenvsub {

    public static void main(String[] args) {
        // Prepare our context and subscriber
        ZeroMQContext context = new ZeroMQContext();
        Socket subscriber = context.createSocket(SocketType.SUB);

        subscriber.connect("tcp://localhost:5563");
        subscriber.subscribe("B");
        while (!Thread.currentThread().isInterrupted()) {
            // Read envelope with address
            String address = subscriber.receiveString();

            if (subscriber.hasMoreFramesToReceive()) {
                // Read message contents
                String contents = subscriber.receiveString();
                System.out.println(address + " : " + contents);
            } else {
                System.out.println("Received an address with no contents.");
            }
        }

        context.terminate();
    }
}
