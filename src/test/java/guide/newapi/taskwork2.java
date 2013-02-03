package guide.newapi;

import org.jeromq.api.*;

/**
 * Task worker - design 2
 * Adds pub-sub flow to receive and respond to kill signal
 */
public class taskwork2 {

    public static void main(String[] args) throws InterruptedException {
        ZeroMQContext context = new ZeroMQContext();

        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.connect("tcp://localhost:5557");

        Socket sender = context.createSocket(SocketType.PUSH);
        sender.connect("tcp://localhost:5558");

        Socket controller = context.createSocket(SocketType.SUB);
        controller.connect("tcp://localhost:5559");
        controller.subscribe("".getBytes());

        Poller poller = context.createPoller();
        poller.register(receiver, PollOption.POLL_IN);
        poller.register(controller, PollOption.POLL_IN);

        while (true) {
            poller.poll();

            if (poller.signaledForInput(receiver)) {

                String message = receiver.receiveString();
                long milliseconds = Long.parseLong(message);

                //  Simple progress indicator for the viewer
                System.out.print(message + '.');
                System.out.flush();

                //  Do the work
                Thread.sleep(milliseconds);

                //  Send results to sink
                sender.send("");
            }
            //  Any waiting controller command acts as 'KILL'
            if (poller.signaledForInput(controller)) {
                break; // Exit loop
            }

        }

        // Finished
        context.terminate();
    }
}
