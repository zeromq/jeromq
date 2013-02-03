package guide.newapi;

import org.jeromq.api.*;

/**
 * Simple request-reply broker
 */
public class RequestReplyBroker {

    public static void main(String[] args) {
        //  Prepare our context and sockets
        ZeroMQContext context = new ZeroMQContext();

        Socket frontend = context.createSocket(SocketType.ROUTER);
        Socket backend = context.createSocket(SocketType.DEALER);
        frontend.bind("tcp://*:5559");
        backend.bind("tcp://*:5560");

        System.out.println("Launched and connected the broker.");

        //  Initialize poll set
        Poller poller = context.createPoller();
        poller.register(frontend, PollOption.POLL_IN);
        poller.register(backend, PollOption.POLL_IN);

        //  Switch messages between sockets
        while (!Thread.currentThread().isInterrupted()) {
            poller.poll();

            if (poller.signaledForInput(frontend)) {
                forward(frontend, backend);
            }
            if (poller.signaledForInput(backend)) {
                forward(backend, frontend);
            }
        }
        //  We never get here but clean up anyhow
        context.terminate();
    }

    private static void forward(Socket fromSocket, Socket toSocket) {
        while (true) {
            byte[] message = fromSocket.receive();
            boolean more = fromSocket.hasMoreFramesToReceive();

            // Broker it
            toSocket.send(message, more ? SendReceiveOption.SEND_MORE : SendReceiveOption.NONE);
            if (!more) {
                break;
            }
        }
    }
}