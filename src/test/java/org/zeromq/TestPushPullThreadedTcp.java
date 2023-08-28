package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

/**
 * Tests a PUSH-PULL dialog with several methods, each component being on a
 * separate thread.
 */
public class TestPushPullThreadedTcp
{
    private static class Worker implements Runnable
    {
        private final int    count;
        private final AtomicBoolean  finished = new AtomicBoolean();
        private int          idx;
        private final Socket receiver;

        public Worker(Socket receiver, int count)
        {
            this.receiver = receiver;
            this.count = count;
        }

        @Override
        public void run()
        {
            idx = 0;
            while (idx < count) {
                if (idx % 5000 == 10) {
                    zmq.ZMQ.msleep(100);
                }
                ZMsg msg = ZMsg.recvMsg(receiver);
                msg.destroy();
                idx++;
            }
            finished.set(true);
        }
    }

    private static class Client implements Runnable
    {
        private final Socket sender;

        private final AtomicBoolean finished = new AtomicBoolean();

        private final int count;

        public Client(Socket sender, int count)
        {
            this.sender = sender;
            this.count = count;
        }

        @Override
        public void run()
        {
            int idx = 0;
            while (idx++ < count) {
                ZMsg msg = new ZMsg();
                msg.add("DATA");
                boolean sent = msg.send(sender);
                assertThat(sent, is(true));
            }
            finished.set(true);
        }
    }

//    @Test
    public void testRepeated() throws Exception
    {
        for (int idx = 0; idx < 2000; ++idx) {
            System.out.println("+++++++++++ " + idx);
            testPushPull1();
            testPushPull500();
            testPushPullWithWatermark();
        }
    }

    @Test
    public void testPushPull1() throws Exception
    {
        test(1);
    }

    @Test
    public void testPushPull500() throws Exception
    {
        System.out.println("Sending 500 messages");
        test(500);
    }

    @Test
    public void testPushPullWithWatermark() throws Exception
    {
        System.out.println("Sending 20000 messages to trigger watermark limit");
        test(20000);
    }

    private void test(int count) throws InterruptedException
    {
        long start = System.currentTimeMillis();

        ExecutorService threadPool = Executors.newFixedThreadPool(2);

        ZContext ctx = new ZContext();

        ZMQ.Socket receiver = ctx.createSocket(SocketType.PULL);
        assertThat(receiver, notNullValue());
        receiver.setImmediate(false);
        final int port = receiver.bindToRandomPort("tcp://localhost");

        ZMQ.Socket sender = ctx.createSocket(SocketType.PUSH);
        assertThat(sender, notNullValue());
        boolean rc = sender.connect("tcp://localhost:" + port);
        assertThat(rc, is(true));

        Worker worker = new Worker(receiver, count);
        Client client = new Client(sender, count);

        threadPool.submit(worker);
        threadPool.submit(client);

        threadPool.shutdown();
        threadPool.awaitTermination(10, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println("Worker received " + worker.idx + " messages");
        assertThat("Unable to send messages", client.finished.get(), is(true));
        assertThat("Unable to receive messages", worker.finished.get(), is(true));

        receiver.close();
        sender.close();
        ctx.close();

        System.out.println("Test done in " + (end - start) + " millis.");
    }

    @Test
    public void testIssue338() throws InterruptedException, IOException
    {
        try (
             final ZSocket pull = new ZSocket(SocketType.PULL);
             final ZSocket push = new ZSocket(SocketType.PUSH)) {
            final String host = "tcp://localhost:" + Utils.findOpenPort();
            pull.bind(host);
            push.connect(host);

            final ExecutorService executor = Executors.newFixedThreadPool(1);
            final int messagesNumber = 300000;
            Runnable receiver = () -> {
                String actual = null;
                int count = messagesNumber;
                while (count-- > 0) {
                    actual = pull.receiveStringUtf8();
                }
                System.out.println("last message: " + actual);
            };
            executor.submit(receiver);

            final String expected = "hello";
            final long start = System.currentTimeMillis();

            for (int idx = 0; idx < messagesNumber; idx++) {
                push.sendStringUtf8(expected + "_" + idx);
            }
            long end = System.currentTimeMillis();
            System.out.println("push time :" + (end - start) + " millisec.");

            executor.shutdown();
            executor.awaitTermination(40, TimeUnit.SECONDS);
            end = System.currentTimeMillis();
            System.out.println("all time :" + (end - start) + " millisec.");
        }
    }
}
