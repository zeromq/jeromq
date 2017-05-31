package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

import java.util.Random;

/**
 * ROUTER-TO-DEALER example
 */
public class rtdealer {
    private static Random random = new Random(System.currentTimeMillis());
    private static final int NUMBER_OF_WORKERS = 10;

    private static class Worker implements Runnable {

        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();
            Socket worker = context.createSocket(SocketType.DEALER);
            GuideHelper.assignPrintableIdentity(worker);

            worker.connect("tcp://localhost:5671");

            int total = 0;
            while (true) {
                //  Tell the broker we're ready for work
                worker.sendWithMoreExpected(""); // this is here to make the envelope look like a REQ envelope.
                worker.send("Hi Boss");

                //  Get workload from broker, until finished
                worker.receive(); //  Envelope delimiter
                String workload = worker.receiveString();
                boolean finished = workload.equals("Fired!");
                if (finished) {
                    System.out.printf("Completed: %d tasks\n", total);
                    break;
                }
                total++;

                //  Do some random work
                try {
                    Thread.sleep(random.nextInt(500) + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            context.terminate();
        }
    }


    /**
     * While this example runs in a single process, that is just to make
     * it easier to start and stop the example. Each thread has its own
     * context and conceptually acts as a separate process.
     */
    public static void main(String[] args) throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        Socket broker = context.createSocket(SocketType.ROUTER);
        broker.bind("tcp://*:5671");

        for (int i = 0; i < NUMBER_OF_WORKERS; i++) {
            new Thread(new Worker()).start();
        }

        //  Run for five seconds and then tell workers to end
        long endTime = System.currentTimeMillis() + 5000;
        int workersFired = 0;
        while (true) {
            //  Next message gives us least recently used worker
            String identity = broker.receiveString();
            broker.receive();  // REQ-style Envelope delimiter, for DEALER/REQ interop.
            broker.receive();  // Actual Response from worker ("Hi Boss")

            broker.sendWithMoreExpected(identity);
            broker.sendWithMoreExpected("");
            //  Encourage workers until it's time to fire them
            if (System.currentTimeMillis() < endTime) {
                broker.send("Work harder");
            } else {
                broker.send("Fired!");
                if (++workersFired == NUMBER_OF_WORKERS) {
                    break;
                }
            }
        }

        context.terminate();
    }
}
