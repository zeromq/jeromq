package guide.newapi;

import org.jeromq.api.SendReceiveOption;
import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Reading from multiple sockets in Java
 * This version uses a simple receive loop (not the recommended approach).
 */
public class msreader {

    public static void main(String[] args) throws Exception {
        //  Prepare our context and sockets
        ZeroMQContext context = new ZeroMQContext();

        // Connect to task ventilator
        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Connect to weather server
        Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("10001 ");

        //  Process messages from both sockets
        //  We prioritize traffic from the task ventilator
        while (!Thread.currentThread().isInterrupted()) {
            //  Process any waiting tasks
            byte[] task;
            while ((task = receiver.receive(SendReceiveOption.DONT_WAIT)) != null) {
                System.out.println("process task");
            }
            //  Process any waiting weather updates
            byte[] update;
            while ((update = subscriber.receive(SendReceiveOption.DONT_WAIT)) != null) {
                System.out.println("process weather update");
            }
            //  No activity, so sleep for 1 millisecond.
            Thread.sleep(1);
        }
        context.terminate();
    }
}
