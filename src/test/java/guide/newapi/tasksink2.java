package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Task sink - design 2
 * Adds pub-sub flow to send kill signal to workers
 */
public class tasksink2 {

    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();
        Socket receiver = context.createSocket(SocketType.PULL);
        receiver.bind("tcp://*:5558");

        // Socket for worker control
        Socket controller = context.createSocket(SocketType.PUB);
        controller.bind("tcp://*:5559");

        //  Wait for start of batch
        receiver.receive();

        //  Start our clock now
        long startTime = System.currentTimeMillis();

        //  Process 100 confirmations
        int taskNumber;
        for (taskNumber = 0; taskNumber < 100; taskNumber++) {
            receiver.receive();
            if ((taskNumber % 10) == 0) {
                System.out.print(":");
            } else {
                System.out.print(".");
            }
        }
        //  Calculate and report duration of batch
        long endTime = System.currentTimeMillis();

        System.out.println("\nTotal elapsed time: " + (endTime - startTime) + " msec");

        //  Send the kill signal to the workers
        controller.send("KILL");

        //  Give it some time to deliver
        Thread.sleep(1000);

        context.terminate();
    }
}
