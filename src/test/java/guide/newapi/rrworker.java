package guide.newapi;

import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Hello World worker
 * Connects REP socket to tcp://*:5560
 * Expects "Hello" from client, replies with "World"
 */
public class rrworker {
    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to talk to server
        org.jeromq.api.Socket responder = context.createSocket(SocketType.REP);
        responder.connect("tcp://localhost:5560");

        while (!Thread.currentThread().isInterrupted()) {
            //  Wait for next request from client
            String string = responder.receiveString();
            System.out.printf("Received request: [%s]\n", string);

            //  Do some 'work'
            Thread.sleep(1000);

            //  Send reply back to client
            responder.send("World");
        }

        //  We never get here but clean up anyhow
        context.terminate();
    }
}
