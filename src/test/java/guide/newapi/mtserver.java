package guide.newapi;

import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

/**
 * Multi threaded Hello World server
 */
public class mtserver {

    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        Socket clients = context.createSocket(SocketType.ROUTER);
        clients.bind("tcp://*:5555");

        Socket workers = context.createSocket(SocketType.DEALER);
        workers.bind("inproc://workers");

        for (int threadNumber = 0; threadNumber < 5; threadNumber++) {
            new Thread(new Worker(context)).start();
        }
        //  Connect work threads to client threads via a queue
        context.startProxy(clients, workers);

        //  We never get here but clean up anyhow
        context.terminate();
    }

    private static class Worker implements Runnable {

        private final ZeroMQContext context;

        private Worker(ZeroMQContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            Socket socket = context.createSocket(SocketType.REP);
            socket.connect("inproc://workers");

            while (true) {
                //  Wait for next request from client
                String request = socket.receiveString();
                System.out.println(Thread.currentThread().getName() + " Received request: [" + request + "]");

                //  Do some 'work'
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                // Send reply back to client
                socket.send("world");
            }
        }

    }
}