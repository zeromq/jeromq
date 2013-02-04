package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

import java.util.Random;

//
//Custom routing (ROUTER to REQ)
//
public class rtreq {

    private static final int NUMBER_OF_WORKERS = 10;

    public static class Worker implements Runnable {

        public void run() {
            Random random = new Random(System.currentTimeMillis());
            ZeroMQContext context = new ZeroMQContext();
            Socket worker = context.createSocket(SocketType.REQ);
            // worker.setIdentity(); will set a random id automatically
            worker.connect("ipc://routing.ipc");

            int total = 0;
            while (true) {
                worker.send("Hi Boss");
                String workload = worker.receiveString();
                if ("Fired!".equals(workload)) {
                    System.out.println(String.format("Processed %d tasks.", total));
                    break;
                }
                total += 1;
                // do some random work
                try {
                    Thread.sleep(random.nextInt(500));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            context.terminate();
        }
    }

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();
        Socket client = context.createSocket(SocketType.ROUTER);
        client.bind("ipc://routing.ipc");

        for (int i = 0; i != NUMBER_OF_WORKERS; i++) {
            new Thread(new Worker()).start();
        }
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 5000;

        int workersFired = 0;
        while (true) {
            //  LRU worker is next waiting in queue
            byte[] address = client.receive();
            byte[] empty = client.receive();
            byte[] ready = client.receive();
            client.sendWithMoreExpected(address);
            client.sendWithMoreExpected("");
            if (System.currentTimeMillis() > endTime) {
                client.send("Fired!");
                if (++workersFired == NUMBER_OF_WORKERS) {
                    break;
                }
            } else {
                client.send("Work Harder!");
            }
        }

        context.terminate();
    }

}
