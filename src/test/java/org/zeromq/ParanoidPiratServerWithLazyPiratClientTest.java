package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class ParanoidPiratServerWithLazyPiratClientTest
{
    private static final int HEARTBEAT_LIVENESS = 3;    //  3-5 is reasonable
    private static final int HEARTBEAT_INTERVAL = 1000; //  msecs

    //  Paranoid Pirate Protocol constants
    private static final String PPP_READY     = "\001"; //  Signals worker is ready
    private static final String PPP_HEARTBEAT = "\002"; //  Signals worker heartbeat

    private static void failTest(String desc, ZMsg msg)
    {
        msg.dump(System.out);
        final StringBuilder builder = new StringBuilder(desc);
        msg.dump(builder);
        fail(builder.toString());
    }

    private static final class Queue implements Runnable
    {
        private final int portQueue;
        private final int portWorkers;

        private final AtomicBoolean active = new AtomicBoolean(true);

        //  Here we define the worker class; a structure and a set of functions that
        //  as constructor, destructor, and methods on worker objects:
        private static class Worker
        {
            final ZFrame address;  //  Address of worker
            final String identity; //  Printable identity
            final long   expiry;   //  Expires at this time

            protected Worker(ZFrame address)
            {
                this.address = address;
                identity = new String(address.getData(), ZMQ.CHARSET);
                expiry = System.currentTimeMillis() + HEARTBEAT_INTERVAL * HEARTBEAT_LIVENESS;
            }

            //  The ready method puts a worker to the end of the ready list:
            protected void ready(final List<Worker> workers)
            {
                final Iterator<Worker> it = workers.iterator();
                while (it.hasNext()) {
                    final Worker worker = it.next();
                    if (identity.equals(worker.identity)) {
                        it.remove();
                        break;
                    }
                }
                workers.add(this);
            }

            //  The next method returns the next available worker address:
            protected static ZFrame next(final List<Worker> workers)
            {
                final Worker worker = workers.remove(0);
                assertThat(worker, notNullValue());
                return worker.address;
            }

            //  The purge method looks for and kills expired workers. We hold workers
            //  from oldest to most recent, so we stop at the first alive worker:
            protected static void purge(List<Worker> workers)
            {
                final Iterator<Worker> it = workers.iterator();
                while (it.hasNext()) {
                    final Worker worker = it.next();
                    if (System.currentTimeMillis() < worker.expiry) {
                        break;
                    }
                    it.remove();
                }
            }
        }

        public Queue(int portQueue, int portWorkers)
        {
            this.portQueue = portQueue;
            this.portWorkers = portWorkers;
        }

        //  The main task is an LRU queue with heartbeating on workers so we can
        //  detect crashed or blocked worker tasks:
        @Override
        public void run()
        {
            Thread.currentThread().setName("Queue");
            final ZContext ctx = new ZContext();
            final Socket frontend = ctx.createSocket(SocketType.ROUTER);
            final Socket backend = ctx.createSocket(SocketType.ROUTER);
            frontend.bind("tcp://*:" + portQueue); //  For clients
            backend.bind("tcp://*:" + portWorkers); //  For workers

            //  List of available workers
            final List<Worker> workers = new ArrayList<>();

            //  Send out heartbeats at regular intervals
            long heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;

            final Poller poller = ctx.createPoller(2);
            poller.register(backend, Poller.POLLIN);
            poller.register(frontend, Poller.POLLIN);

            while (active.get()) {
                final boolean workersAvailable = !workers.isEmpty();
                final int rc = poller.poll(HEARTBEAT_INTERVAL);
                if (rc == -1) {
                    break; //  Interrupted
                }

                //  Handle worker activity on backend
                if (poller.pollin(0)) {
                    //  Use worker address for LRU routing
                    final ZMsg msg = ZMsg.recvMsg(backend);
                    if (msg == null) {
                        break; //  Interrupted
                    }

                    //  Any sign of life from worker means it's ready
                    final ZFrame address = msg.unwrap();
                    final Worker worker = new Worker(address);
                    worker.ready(workers);

                    //  Validate control message, or return reply to client
                    if (msg.size() == 1) {
                        final ZFrame frame = msg.getFirst();
                        final String data = new String(frame.getData(), ZMQ.CHARSET);
                        if (!data.equals(PPP_READY) && !data.equals(PPP_HEARTBEAT)) {
                            failTest("E: Queue ---- invalid message from worker", msg);
                        }
                        msg.destroy();
                    }
                    else {
                        msg.send(frontend);
                    }
                }
                if (workersAvailable && poller.pollin(1)) {
                    //  Now get next client request, route to next worker
                    final ZMsg msg = ZMsg.recvMsg(frontend);
                    if (msg == null) {
                        break; //  Interrupted
                    }
                    msg.push(Worker.next(workers));
                    msg.send(backend);
                }

                //  We handle heartbeating after any socket activity. First we send
                //  heartbeats to any idle workers if it's time. Then we purge any
                //  dead workers:

                if (System.currentTimeMillis() >= heartbeatAt) {
                    for (final Worker worker : workers) {
                        worker.address.send(backend, ZFrame.REUSE + ZFrame.MORE);
                        final ZFrame frame = new ZFrame(PPP_HEARTBEAT);
                        frame.send(backend, 0);
                    }
                    heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
                }
                Worker.purge(workers);
            }

            //  When we're done, clean up properly
            workers.clear();
            ctx.close();
        }

    }

    private static final class Worker implements Runnable
    {
        private final int portWorkers;

        private static final int INTERVAL_INIT = 1000;  //  Initial reconnect
        private static final int INTERVAL_MAX  = 32000; //  After exponential backoff

        //  Helper function that returns a new configured socket
        //  connected to the Paranoid Pirate queue

        private Worker(int portWorkers)
        {
            this.portWorkers = portWorkers;
        }

        private Socket workerSocket(ZContext ctx)
        {
            final Socket worker = ctx.createSocket(SocketType.DEALER);
            worker.connect("tcp://localhost:" + portWorkers);

            //  Tell queue we're ready for work
            System.out.println("I: Worker - ready");
            final ZFrame frame = new ZFrame(PPP_READY);
            frame.send(worker, 0);

            return worker;
        }

        //  We have a single task, which implements the worker side of the
        //  Paranoid Pirate Protocol (PPP). The interesting parts here are
        //  the heartbeating, which lets the worker detect if the queue has
        //  died, and vice-versa:

        @Override
        public void run()
        {
            Thread.currentThread().setName("Worker");
            final ZContext ctx = new ZContext();
            Socket worker = workerSocket(ctx);

            final Poller poller = ctx.createPoller(1);
            poller.register(worker, Poller.POLLIN);

            //  If liveness hits zero, queue is considered disconnected
            int liveness = HEARTBEAT_LIVENESS;
            int interval = INTERVAL_INIT;

            //  Send out heartbeats at regular intervals
            long heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;

            int cycles = 0;
            while (true) {
                final int rc = poller.poll(HEARTBEAT_INTERVAL);
                if (rc == -1) {
                    break; //  Interrupted
                }

                if (poller.pollin(0)) {
                    //  Get message
                    //  - 3-part envelope + content -> request
                    //  - 1-part HEARTBEAT -> heartbeat
                    final ZMsg msg = ZMsg.recvMsg(worker);
                    if (msg == null) {
                        break; //  Interrupted
                    }

                    //  To test the robustness of the queue implementation we
                    //  simulate various typical problems, such as the worker
                    //  crashing, or running very slowly. We do this after a few
                    //  cycles so that the architecture can get up and running
                    //  first:
                    if (msg.size() == 3) {
                        cycles++;
                        if (cycles % 10 == 0) {
                            System.out.println("I: Worker - simulating a crash");
                            msg.destroy();
                            break;
                        }
                        else if (cycles % 5 == 0) {
                            System.out.println("I: Worker - simulating CPU overload");
                            try {
                                Thread.sleep(3000);
                            }
                            catch (InterruptedException e) {
                                break;
                            }
                        }
                        System.out.println("I: Worker - normal reply");
                        msg.send(worker);
                        liveness = HEARTBEAT_LIVENESS;
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            break;
                        } //  Do some heavy work
                    }
                    else
                    //  When we get a heartbeat message from the queue, it means the
                    //  queue was (recently) alive, so reset our liveness indicator:
                    if (msg.size() == 1) {
                        final ZFrame frame = msg.getFirst();
                        if (PPP_HEARTBEAT.equals(new String(frame.getData(), ZMQ.CHARSET))) {
                            liveness = HEARTBEAT_LIVENESS;
                        }
                        else {
                            failTest("E: Worker - invalid message", msg);
                        }
                        msg.destroy();
                    }
                    else {
                        failTest("E: Worker - invalid message", msg);
                    }
                    interval = INTERVAL_INIT;
                }
                else
                //  If the queue hasn't sent us heartbeats in a while, destroy the
                //  socket and reconnect. This is the simplest most brutal way of
                //  discarding any messages we might have sent in the meantime://
                if (--liveness == 0) {
                    System.out.println("W: Worker ---- heartbeat failure, can't reach queue");
                    System.out.printf("W: Worker ---- reconnecting in %sd msec\n", interval);
                    try {
                        Thread.sleep(interval);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (interval < INTERVAL_MAX) {
                        interval *= 2;
                    }
                    worker.close();
                    worker = workerSocket(ctx);
                    liveness = HEARTBEAT_LIVENESS;
                }

                //  Send heartbeat to queue if it's time
                if (System.currentTimeMillis() > heartbeatAt) {
                    heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
                    System.out.println("I: Worker - heartbeat");
                    final ZFrame frame = new ZFrame(PPP_HEARTBEAT);
                    frame.send(worker, 0);
                }
            }
            ctx.close();
        }
    }

    private static final class Client implements Runnable
    {
        private final int portQueue;

        private static final int REQUEST_TIMEOUT = 2500; //  msecs, (> 1000!)
        private static final int REQUEST_RETRIES = 3;    //  Before we abandon

        public Client(int portQueue)
        {
            this.portQueue = portQueue;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Client");
            final ZContext ctx = new ZContext();
            System.out.println("I: Client - connecting to server");
            Socket client = ctx.createSocket(SocketType.REQ);
            assert (client != null);
            client.connect("tcp://localhost:" + portQueue);

            final Poller poller = ctx.createPoller(1);
            poller.register(client, Poller.POLLIN);

            int sequence = 0;
            int retriesLeft = REQUEST_RETRIES;
            while (retriesLeft > 0 && !Thread.currentThread().isInterrupted()) {
                //  We send a request, then we work to get a reply
                final String request = String.format("%d", ++sequence);
                client.send(request);

                int expectReply = 1;
                while (expectReply > 0) {
                    //  Poll socket for a reply, with timeout
                    int rc = poller.poll(REQUEST_TIMEOUT);
                    if (rc == -1) {
                        break; //  Interrupted
                    }

                    //  Here we process a server reply and exit our loop if the
                    //  reply is valid. If we didn't a reply we close the client
                    //  socket and resend the request. We try a number of times
                    //  before finally abandoning:

                    if (poller.pollin(0)) {
                        //  We got a reply from the server, must match getSequence
                        final String reply = client.recvStr();
                        if (reply == null) {
                            break; //  Interrupted
                        }
                        if (Integer.parseInt(reply) == sequence) {
                            System.out.printf("I: Client - server replied OK (%s)\n", reply);
                            retriesLeft = REQUEST_RETRIES;
                            expectReply = 0;
                        }
                        else {
                            System.out.printf("E: Client ---- malformed reply from server: %s\n", reply);
                        }

                    }
                    else if (--retriesLeft == 0) {
                        System.out.println("E: Client - server seems to be offline, abandoning");
                        break;
                    }
                    else {
                        System.out.println("W: Client - no response from server, retrying");
                        //  Old socket is confused; close it and open a new one
                        poller.unregister(client);
                        client.close();
                        System.out.println("I: Client - reconnecting to server");
                        client = ctx.createSocket(SocketType.REQ);
                        client.connect("tcp://localhost:" + portQueue);
                        poller.register(client, Poller.POLLIN);
                        //  Send request again, on new socket
                        client.send(request);
                    }
                }
            }
            ctx.close();
        }
    }

    //    @Test
    public void testRepeated() throws IOException, InterruptedException, ExecutionException
    {
        for (int idx = 0; idx < 300; ++idx) {
            System.out.println("+++++++++ " + idx);
            testIssue408();
        }
    }

    @Test
    public void testIssue408() throws IOException, InterruptedException, ExecutionException
    {
        final int portQueue = Utils.findOpenPort();
        final int portWorkers = Utils.findOpenPort();

        final ExecutorService service = Executors.newFixedThreadPool(4);

        final long start = System.currentTimeMillis();

        final Queue queue = new Queue(portQueue, portWorkers);
        service.submit(queue);
        final Future<?> worker = service.submit(new Worker(portWorkers));
        service.submit(() -> {
            try {
                worker.get();
                System.out.println("I: Rebooter - restarting new worker after crash ++++++++++++");
                service.submit(new Worker(portWorkers));
            }
            catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        final Future<?> client = service.submit(new Client(portQueue));

        client.get();
        // client is terminated, time to stop the queue to complete the test.
        queue.active.set(false);

        service.shutdown();
        assertThat(service.awaitTermination(20, TimeUnit.SECONDS), is(true));

        final long end = System.currentTimeMillis();
        System.out.printf("Test with Paranoid Server and Lazy client completed in %s millis%n", end - start);
    }
}
