package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Task sink in Java
 * Binds PULL socket to tcp://localhost:5558
 * Collects results from workers via that socket
 */
public class tasksink {

    public static void main(String[] args) throws Exception {
        //  Prepare our context and socket
        ZeroMQContext context = new ZeroMQContext();
        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.bind("tcp://*:5558");

        //  Wait for start of batch
        receiver.receive();

        //  Start our clock now
        long startTime = System.currentTimeMillis();

        //  Process 100 confirmations
        for (int task_nbr = 0; task_nbr < 100; task_nbr++) {
            String task = receiver.receiveString();
            if ((task_nbr % 10) == 0) {
                System.out.print(":");
            } else {
                System.out.print(".");
            }
        }
        //  Calculate and report duration of batch
        long tend = System.currentTimeMillis();

        System.out.println("\nTotal elapsed time: " + (tend - startTime) + " msec");
        context.terminate();
    }
}
