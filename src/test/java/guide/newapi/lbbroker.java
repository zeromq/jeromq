package guide.newapi;

import org.jeromq.api.PollOption;
import org.jeromq.api.Socket;
import org.jeromq.api.SocketType;
import org.jeromq.api.ZeroMQContext;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Load-balancing broker.
 * Note: this example never exits, since the WorkerTasks are not interrupted, nor signalled to exit.
 */
public class lbbroker {

    private static final int NUMBER_OF_CLIENTS = 10;
    private static final int NUMBER_OF_WORKERS = 3;

    /**
     * Basic request-reply client using REQ socket
     */
    private static class ClientTask implements Runnable {
        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();

            //  Prepare our context and sockets
            Socket client = context.createSocket(SocketType.REQ);
            GuideHelper.assignPrintableIdentity(client);

            client.connect("ipc://frontend.ipc");

            //  Send request, get reply
            client.send("HELLO");
            String reply = client.receiveString();
            System.out.println("Client: " + reply);

            context.terminate();
        }
    }

    /**
     * While this example runs in a single process, that is just to make
     * it easier to start and stop the example. Each thread has its own
     * context and conceptually acts as a separate process.
     * This is the worker task, using a REQ socket to do load-balancing.
     */
    private static class WorkerTask implements Runnable {
        @Override
        public void run() {
            ZeroMQContext context = new ZeroMQContext();
            //  Prepare our context and sockets
            Socket worker = context.createSocket(SocketType.REQ);
            GuideHelper.assignPrintableIdentity(worker);

            worker.connect("ipc://backend.ipc");

            //  Tell backend we're ready for work
            worker.send("READY");

            while (!Thread.currentThread().isInterrupted()) {
                // client response is [client identity][empty][request]
                String address = worker.receiveString();
                worker.receiveString();  //empty
                String request = worker.receiveString();
                System.out.println("Worker: " + request);

                worker.sendWithMoreExpected(address);
                worker.sendWithMoreExpected("");
                worker.send("OK");
            }
            //we never get here...
            context.terminate();
        }
    }

    /**
     * This is the main task. It starts the clients and workers, and then
     * routes requests between the two layers. Workers signal READY when
     * they start; after that we treat them as ready when they reply with
     * a response back to a client. The load-balancing data structure is
     * just a queue of next available workers.
     */
    public static void main(String[] args) {
        ZeroMQContext context = new ZeroMQContext();
        //  Prepare our context and sockets
        Socket frontend = context.createSocket(SocketType.ROUTER);
        Socket backend = context.createSocket(SocketType.ROUTER);
        frontend.bind("ipc://frontend.ipc");
        backend.bind("ipc://backend.ipc");

        int clientNumber;
        for (clientNumber = 0; clientNumber < NUMBER_OF_CLIENTS; clientNumber++) {
            new Thread(new ClientTask()).start();
        }

        for (int i = 0; i < NUMBER_OF_WORKERS; i++) {
            new Thread(new WorkerTask()).start();
        }

        //  Here is the main loop for the least-recently-used queue. It has two
        //  sockets; a frontend for clients and a backend for workers. It polls
        //  the backend in all cases, and polls the frontend only when there are
        //  one or more workers ready. This is a neat way to use 0MQ's own queues
        //  to hold messages we're not ready to process yet. When we get a client
        //  reply, we pop the next available worker, and send the request to it,
        //  including the originating client identity. When a worker replies, we
        //  re-queue that worker, and we forward the reply to the original client,
        //  using the reply envelope.

        //  Queue of available workers
        Queue<String> workerQueue = new LinkedList<String>();

        while (!Thread.currentThread().isInterrupted()) {
            //  Initialize poll set
            org.jeromq.api.Poller poller = context.createPoller();

            //  Always poll for worker activity on backend
            poller.register(backend, PollOption.POLL_IN);

            //  Poll front-end only if we have available workers
            if (workerQueue.size() > 0) {
                poller.register(frontend, PollOption.POLL_IN);
            }

            //todo what is the negative result supposed to represent here?
            if (poller.poll() < 0) {
                break;
            }

            //  Handle worker activity on backend
            if (poller.signaledForInput(backend)) {
                //  Worker request is [worker address][empty][client address][empty][worker reply]
                //  Queue worker address for LRU routing
                String workerAddress = backend.receiveString();
                workerQueue.add(workerAddress);
                //  Second frame is empty
                backend.receiveString(); //empty
                //  Third frame is READY or else a client reply address
                String clientAddress = backend.receiveString();

                //  If client reply, send rest back to frontend
                if (!clientAddress.equals("READY")) {
                    backend.receiveString(); //empty

                    String reply = backend.receiveString();
                    frontend.sendWithMoreExpected(clientAddress);
                    frontend.sendWithMoreExpected("");
                    frontend.send(reply);

                    if (--clientNumber == 0) {
                        break;
                    }
                }

            }

            if (poller.signaledForInput(frontend)) {
                //  Now get next client request, route to LRU worker
                //  Client request is [address][empty][request]
                String clientAddress = frontend.receiveString();
                frontend.receiveString(); //empty
                String request = frontend.receiveString();

                String workerAddress = workerQueue.poll();

                backend.sendWithMoreExpected(workerAddress);
                backend.sendWithMoreExpected("");
                backend.sendWithMoreExpected(clientAddress);
                backend.sendWithMoreExpected("");
                backend.send(request);
            }
        }

        context.terminate();
    }

}
