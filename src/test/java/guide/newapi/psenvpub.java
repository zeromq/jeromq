package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Pubsub envelope publisher
 */
public class psenvpub {

    public static void main(String[] args) throws Exception {
        // Prepare our context and publisher
        ZeroMQContext context = new ZeroMQContext();
        Socket publisher = context.createSocket(SocketType.PUB);

        publisher.bind("tcp://*:5563");
        while (!Thread.currentThread().isInterrupted()) {
            // Write two messages, each with an envelope and content
            publisher.sendWithMoreExpected("A");
            publisher.send("We don't want to see this");

            publisher.sendWithMoreExpected("B");
            publisher.send("We would like to see this");

            Thread.sleep(1000);
        }
        context.terminate();
    }
}
