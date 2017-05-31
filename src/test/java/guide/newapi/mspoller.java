package guide.newapi;

import org.jeromq.api.*;

/**
 * Reading from multiple sockets in Java
 * This version uses a Poller.
 */
public class mspoller {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        // Connect to task ventilator
        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Connect to weather server
        Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("10001 ");

        //  Initialize poll set
        Poller poller = context.createPoller();
        poller.register(receiver, PollOption.POLL_IN);
        poller.register(subscriber, PollOption.POLL_IN);

        //  Process messages from both sockets
        while (!Thread.currentThread().isInterrupted()) {
            poller.poll();
            if (poller.signaledForInput(receiver)) {
                byte[] message = receiver.receive();
                System.out.println("Process task");
            }
            if (poller.signaledForInput(subscriber)) {
                byte[] message = subscriber.receive();
                System.out.println("Process weather update");
            }
        }
        context.terminate();
    }
}
