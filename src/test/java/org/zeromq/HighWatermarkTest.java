package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;
import zmq.util.AndroidIgnore;

public class HighWatermarkTest
{
    private static final int N_MESSAGES   = 30000;
    private static final int MESSAGE_SIZE = 50;

    private static final int FILL_WATERMARK = 3000;
    private static final int TRACE          = 7000;

    public static class Dispatcher implements Runnable
    {
        private final String control;
        private final boolean trace;
        private final String  msg;
        private final ZContext context;

        private ZMQ.Socket sender;

        Dispatcher(String msg, String control, boolean trace)
        {
            this.msg = msg;
            this.control = control;
            this.trace = trace;
            this.context = new ZContext(1);
        }

        //  Socket to send messages on
        private String initDispatch()
        {
            sender = context.createSocket(SocketType.PUSH);
            sender.setImmediate(false);
            sender.bind("tcp://*:*");
            return sender.getLastEndpoint();
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Dispatcher");

            try {
                ZMQ.Socket controller = context.createSocket(SocketType.SUB);
                controller.subscribe(ZMQ.SUBSCRIPTION_ALL);
                controller.connect(control);

                System.out.println("Sending " + N_MESSAGES + " tasks (" + MESSAGE_SIZE + "b) to workers\n");

                //  The first message is "0" and signals start of batch
                sender.send("0", 0);

                System.out.println("Started dispatcher on " + sender.getLastEndpoint());

                //  Send N_MESSAGES tasks
                for (int taskNbr = 0; taskNbr < N_MESSAGES; taskNbr++) {
                    sender.send(taskNbr + " - " + msg, 0);
                    if (trace) {
                        System.out.println(taskNbr + " - Dispatcher sent msg");
                    }
                }

                System.out.println("Dispatcher finished, awaiting for collector finish");
                controller.recvStr();
                // We can finish NOW!
            }
            finally {
                if (trace) {
                    System.out.println("Dispatcher closing.");
                }
                context.close();
                System.out.println("Dispatcher done.");
            }
        }
    }

    public static class Worker implements Runnable
    {
        private final String control;

        private final String  dispatch;
        private final String  collect;
        private final boolean trace;
        private final int     index;

        public Worker(String dispatch, String collect, String control, int index, boolean trace)
        {
            this.dispatch = dispatch;
            this.collect = collect;
            this.control = control;
            this.index = index;
            this.trace = trace;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Worker #" + index);

            ZContext context = new ZContext(1);

            //  Socket to receive messages on
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            receiver.setImmediate(false);
            receiver.connect(dispatch);

            //  Socket to send messages to
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.setImmediate(false);
            sender.connect(collect);

            ZMQ.Socket controller = context.createSocket(SocketType.SUB);
            controller.subscribe(ZMQ.SUBSCRIPTION_ALL);
            controller.connect(control);

            ZMQ.Poller poller = context.createPoller(3);
            poller.register(receiver, ZMQ.Poller.POLLIN);
            poller.register(sender, ZMQ.Poller.POLLOUT);
            poller.register(controller, ZMQ.Poller.POLLIN);

            int idx = 0;

            try {
                System.out.println("Started worker process #" + index);

                //  Process tasks forever
                while (!Thread.currentThread().isInterrupted()) {
                    poller.poll(1000);
                    boolean in = poller.pollin(0);
                    boolean out = poller.pollout(1);
                    boolean ctrl = poller.pollin(2);
                    if (in && out) {
                        String msg = new String(receiver.recv(0), ZMQ.CHARSET).trim();
                        //  Simple progress indicator for the viewer
                        if (trace) {
                            System.out.println("Worker #" + index + " recv " + msg);
                        }
                        else {
                            if (idx % TRACE == 0) {
                                System.out.println("Worker #" + index + " recv " + idx + " messages");
                            }
                        }
                        ++idx;
                        // the pipes reach the watermark once in a while
                        if (idx % FILL_WATERMARK == 10) {
                            LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS));
                        }

                        //  Send results to sink
                        sender.send("#" + index + " - " + msg, 0);
                    }
                    if (ctrl) {
                        break;
                    }
                }
            }
            finally {
                if (trace) {
                    System.out.println("Worker #" + index + " closing.");
                }
                poller.close();
                context.close();
                System.out.println("Worker #" + index + " done.");
            }
        }
    }

    public static class Collector implements Runnable
    {
        private final ZContext context;

        private final boolean trace;
        private final String  msg;

        private final int workers;

        private final AtomicBoolean success = new AtomicBoolean();

        private ZMQ.Socket receiver;
        private ZMQ.Socket controller;

        Collector(String msg, int workers, boolean trace)
        {
            this.msg = msg;
            this.workers = workers;
            this.trace = trace;
            this.context = new ZContext(1);
        }

        private String initCollect()
        {
            receiver = context.createSocket(SocketType.PULL);
            receiver.setImmediate(false);
            receiver.bind("tcp://*:*");
            return receiver.getLastEndpoint();
        }

        private String initControl()
        {
            controller = context.createSocket(SocketType.PUB);
            controller.bind("tcp://*:*");
            return controller.getLastEndpoint();
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Collector");
            if (trace) {
                System.out.println("Started collector on " + receiver.getLastEndpoint());
            }

            try {
                //  Wait for start of batch
                String msg = new String(receiver.recv(0), ZMQ.CHARSET);

                if (trace) {
                    System.out.println("Collector started");
                }

                for (int taskNbr = 0; taskNbr < N_MESSAGES; taskNbr++) {
                    if (taskNbr % FILL_WATERMARK == 10) {
                        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS));
                    }
                    msg = new String(receiver.recv(0), ZMQ.CHARSET).trim();
                    if (trace) {
                        System.out.println("Collector recv : " + taskNbr + " -> " + msg);
                    }
                    else if (taskNbr % TRACE == 0 || taskNbr == 100) {
                        System.out.println("Collector recv : " + taskNbr + " messages ");
                    }

                    // Test received messages
                    if (workers == 1) {
                        if (msg.indexOf(" - " + taskNbr + " - ") != 2) {
                            System.out.println(taskNbr + " - Message was not correct ! " + msg);
                            break;
                        }
                    }
                    if (!msg.endsWith(this.msg) && !msg.endsWith(" - 0")) {
                        System.out.println(taskNbr + " - Message was not correct ! " + msg);
                        break;
                    }
                }

                controller.send("FINISH"); // Signal dispatcher to finish
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
            finally {
                context.close();
                System.out.println("Collector done.");
            }
            success.set(true);
        }
    }

    @Test
    public void testReliabilityOnWatermark() throws InterruptedException
    {
        testWatermark(1);
    }

    @Test
    @AndroidIgnore
    public void testReliabilityOnWatermark2() throws InterruptedException
    {
        testWatermark(2);
    }

    private void testWatermark(int workers) throws InterruptedException
    {
        long start = System.currentTimeMillis();

        ExecutorService threadPool = Executors.newFixedThreadPool(workers + 2);

        String msg = randomString(MESSAGE_SIZE);

        Collector collector = new Collector(msg, workers, false);
        String collect = collector.initCollect();
        String control = collector.initControl();

        Dispatcher dispatcher = new Dispatcher(msg, control, false);
        String dispatch = dispatcher.initDispatch();
        threadPool.submit(dispatcher);
        threadPool.submit(collector);
        for (int idx = 0; idx < workers; ++idx) {
            threadPool.submit(new Worker(dispatch, collect, control, idx + 1, false));
        }

        threadPool.shutdown();
        threadPool.awaitTermination(360, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        assertThat(collector.success.get(), is(true));
        System.out.println("Test done in " + (end - start) + " millis.");
    }

    /*--------------------------------------------------------------*/

    private static final String       ABC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom rnd = new SecureRandom();

    private static String randomString(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ABC.charAt(rnd.nextInt(ABC.length())));
        }
        return sb.toString();
    }
}
