package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Task worker in Java
 * Connects PULL socket to tcp://localhost:5557
 * Collects workloads from ventilator via that socket
 * Connects PUSH socket to tcp://localhost:5558
 * Sends results to sink via that socket
 */
public class TaskWorker {

    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to receive messages on
        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Socket to send messages to
        Socket sender = context.createSocket(SocketType.PUSH);
        sender.connect("tcp://localhost:5558");

        //  Process tasks forever
        while (!Thread.currentThread().isInterrupted()) {
            String task = receiver.receiveString();
            long millis = Long.parseLong(task);
            //  Simple progress indicator for the viewer
            System.out.flush();
            System.out.print(task + '.');

            //  Do the work
            Thread.sleep(millis);

            //  Send results to sink
            sender.send("");
        }

        context.terminate();
    }
}