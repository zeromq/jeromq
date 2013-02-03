package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

import java.util.Random;

/**
 * Task ventilator in Java
 * Binds PUSH socket to tcp://localhost:5557
 * Sends batch of tasks to workers via that socket
 */
public class TaskVentilator {

    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        //  Socket to send messages on
        Socket sender = context.createSocket(SocketType.PUSH);
        sender.bind("tcp://*:5557");

        //  Socket to send messages on
        Socket sink = context.createSocket(SocketType.PUSH);
        sink.connect("tcp://localhost:5558");

        System.out.println("Press Enter when the workers are ready: ");
        System.in.read();
        System.out.println("Sending tasks to workers\n");

        //  The first message is "0" and signals start of batch
        sink.send("0");

        //  Initialize random number generator
        Random random = new Random(System.currentTimeMillis());

        //  Send 100 tasks
        int totalExpectedCost = 0;
        int taskNumber;
        for (taskNumber = 0; taskNumber < 100; taskNumber++) {
            // Random workload from 1 to 100 millis
            int workload = random.nextInt(100) + 1;
            totalExpectedCost += workload;
            System.out.print(workload + ".");
            sender.send(String.format("%d", workload));
        }
        System.out.println("Total expected cost: " + totalExpectedCost + " msec");
        Thread.sleep(1000);  //  Give 0MQ time to deliver

        context.terminate();
    }
}
