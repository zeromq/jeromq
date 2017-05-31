package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Synchronized publisher.
 */
public class syncpub {
    /**
     * We wait for 10 subscribers
     */
    protected static int SUBSCRIBERS_EXPECTED = 10;

    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();
        //  Socket to talk to clients
        Socket publisher = context.createSocket(SocketType.PUB);
        publisher.setLinger(5000);

        // In 0MQ 3.x pub socket could drop messages if sub can follow the generation of pub messages
        publisher.setSendHighWaterMark(0);
        publisher.bind("tcp://*:5561");

        //  Socket to receive signals
        Socket syncService = context.createSocket(SocketType.REP);
        syncService.bind("tcp://*:5562");

        System.out.println("Waiting subscribers");
        //  Get synchronization from subscribers
        int subscribers = 0;
        while (subscribers < SUBSCRIBERS_EXPECTED) {
            // wait for synchronization request
            syncService.receive();

            // send synchronization reply
            syncService.send("");
            subscribers++;
        }
        //  Now broadcast exactly 1M updates followed by END
        System.out.println("Broadcasting messages");

        for (int i = 0; i < 1000000; i++) {
            publisher.send("Rhubarb");
        }

        publisher.send("END");

        // clean up
        Thread.sleep(1000); //to allow the "END" message to happen
        context.terminate();
    }
}