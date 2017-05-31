package guide.newapi;

import org.jeromq.api.*;

import java.util.Random;

/**
 * Asynchronous client-to-server (DEALER to ROUTER)
 * <p/>
 * While this example runs in a single process, that is just to make
 * it easier to start and stop the example. Each task has its own
 * context and conceptually acts as a separate process.
 */
public class asyncsrv {

    private static Random rand = new Random(System.nanoTime());

    /**
     * This is our client task
     * It connects to the server, and then sends a request once per second
     * It collects responses as they arrive, and it prints them out. We will
     * run several client tasks in parallel, each with a different random ID.
     */
    private static class ClientTask implements Runnable {

        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();
            Socket client = context.createSocket(SocketType.DEALER);

            //  Set random identity to make tracing easier
            String identity = String.format("%04X-%04X", rand.nextInt(), rand.nextInt());
            client.setIdentity(identity.getBytes());
            client.connect("tcp://localhost:5570");

            Poller poller = context.createPoller();
            poller.register(client);

            int request_nbr = 0;
            while (true) {
                //  Tick one hundred times per second, pulling in arriving messages
                for (int centitick = 0; centitick < 100; centitick++) {
                    poller.poll(10);
                    if (client.canReadWithoutBlocking()) {
                        RoutedMessage message = client.receiveRoutedMessage();
                        GuideHelper.print(identity, message.getPayload());
                    }
                }
                client.send(String.format("request #%d", ++request_nbr));
            }
            //context.destroy();
        }
    }

    /**
     * This is our server task.
     * It uses the multithreaded server model to deal requests out to a pool
     * of workers and route replies back to clients. One worker can handle
     * one request at a time but one client can talk to multiple workers at
     * once.
     */
    private static class ServerTask implements Runnable {

        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();

            //  Frontend socket talks to clients over TCP
            Socket frontend = context.createSocket(SocketType.ROUTER);
            frontend.bind("tcp://*:5570");

            //  Backend socket talks to workers over inproc
            Socket backend = context.createSocket(SocketType.DEALER);
            backend.bind("inproc://backend");

            //  Launch pool of worker threads, precise number is not critical
            for (int threadNumber = 0; threadNumber < 150; threadNumber++) {
                new Thread(new ServerWorker(context), "serverWorker-" + threadNumber).start();
            }

            //  Connect backend to frontend via a proxy
            boolean result = context.startProxy(frontend, backend);
            System.out.println("result = " + result);
//            context.terminate();
        }
    }

    /**
     * Each worker task works on one request at a time and sends a random number
     * of replies back, with random delays between replies:
     */
    private static class ServerWorker implements Runnable {
        private ZeroMQContext context;

        public ServerWorker(ZeroMQContext context) {
            this.context = context;
        }

        public void run() {
            Socket worker = context.createSocket(SocketType.DEALER);
            worker.connect("inproc://backend");

            while (true) {
                //  The DEALER socket gives us the address envelope and message
                Message message = worker.receiveMessage();
                //  Send 0..4 replies back
                int replies = rand.nextInt(5);
                for (int reply = 0; reply < replies; reply++) {
                    //  Sleep for some fraction of a second
                    try {
                        Thread.sleep(rand.nextInt(1000) + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    worker.send(message);
                }
            }
        }
    }

    /**
     * The main thread simply starts several clients, and a server, and then
     * waits for the server to finish.
     */
    public static void main(String[] args) throws Exception {
        new Thread(new ClientTask(), "clientThread-1").start();
        new Thread(new ClientTask(), "clientThread-2").start();
        new Thread(new ClientTask(), "clientThread-3").start();
        new Thread(new ServerTask(), "serverThread").start();
    }
}