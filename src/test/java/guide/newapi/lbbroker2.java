package guide.newapi;

import org.jeromq.api.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import static org.jeromq.api.SocketType.ROUTER;

/**
 * Load-balancing broker
 * Demonstrates use of the high level API
 */
public class lbbroker2 {
    private static final int NBR_CLIENTS = 10;
    private static final int NBR_WORKERS = 3;
    private static byte[] WORKER_READY = {'\001'};  //  Signals worker is ready

    /**
     * Basic request-reply client using REQ socket
     */
    private static class ClientTask implements Runnable {
        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();

            //  Prepare our context and sockets
            org.jeromq.api.Socket client = context.createSocket(SocketType.REQ);
            GuideHelper.assignPrintableIdentity(client);

            client.connect("ipc://frontend.ipc");

            //  Send request, get reply
            client.send(new Message("HELLO"));
            Message reply = client.receiveMessage();
            System.out.println("Client: " + reply.getFirstFrameAsString());

            context.terminate();
        }
    }

    /**
     * Worker using REQ socket to do load-balancing
     */
    private static class WorkerTask implements Runnable {
        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();

            //  Prepare our context and sockets
            org.jeromq.api.Socket worker = context.createSocket(SocketType.REQ);
            GuideHelper.assignPrintableIdentity(worker);

            worker.connect("ipc://backend.ipc");

            //  Tell backend we're ready for work
            worker.send(new Message(WORKER_READY));

            while (!Thread.currentThread().isInterrupted()) {
                Message workMessage = worker.receiveMessage();
                Message response = new Message(workMessage);
                response.replaceLast("OK");
                worker.send(response);
            }
            context.terminate();
        }
    }

    /**
     * This is the main task. This has the identical functionality to
     * the previous lbbroker example but uses higher level classes to start child threads
     * to hold the list of workers, and to read and send messages:
     */
    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();

        //  Prepare our context and sockets
        org.jeromq.api.Socket frontend = context.createSocket(ROUTER);
        org.jeromq.api.Socket backend = context.createSocket(ROUTER);
        frontend.bind("ipc://frontend.ipc");
        backend.bind("ipc://backend.ipc");

        int clientNumber;
        for (clientNumber = 0; clientNumber < NBR_CLIENTS; clientNumber++) {
            new Thread(new ClientTask()).start();
        }

        for (int workerNbr = 0; workerNbr < NBR_WORKERS; workerNbr++) {
            new Thread(new WorkerTask()).start();
        }

        //  Queue of available workers
        Queue<String> workerAddressQueue = new LinkedList<String>();

        //  Here is the main loop for the load-balancer. It works the same way
        //  as the previous example, but is a lot shorter because ZMsg class gives
        //  us an API that does more with fewer calls:

        while (!Thread.currentThread().isInterrupted()) {
            //  Initialize poll set
            Poller items = context.createPoller();

            //  Always poll for worker activity on backend
            items.register(backend, PollOption.POLL_IN);

            //  Poll front-end only if we have available workers
            if (workerAddressQueue.size() > 0) {
                items.register(frontend, PollOption.POLL_IN);
            }

            //todo figure out what this API should look like. What does negative poll result even mean in the jzmq?
            if (items.poll() < 0) {
                break;
            }

            //  Handle worker activity on backend
            if (items.signaledForInput(backend)) {
                RouterMessage message = backend.receiveRouterMessage();

                String identity = message.getIdentityAsString();
                //  Queue worker address for LRU routing
                workerAddressQueue.add(identity);

                //  Forward message to client if it's not a READY
                Message payload = message.getPayload();
                if (!Arrays.equals(payload.getFirstFrame(), WORKER_READY)) {
                    frontend.send(payload);
                }
            }

            if (items.signaledForInput(frontend)) {
                //  Get client request, route to first available worker
                Message message = frontend.receiveMessage();

                Message response = RouterMessage.build().copy(message).withAddress(workerAddressQueue.poll()).create();
                backend.send(response);
            }
        }

        context.terminate();
    }

}
