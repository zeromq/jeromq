package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests a PUSH-PULL dialog with several methods, each component being on a
 * separate thread.
 */
@Ignore
public class TestPushPullThreadedTcp
{
    private class Worker implements Runnable
    {
        private final String host;
        private final int    count;
        final AtomicBoolean  finished = new AtomicBoolean();
        private int          idx;

        public Worker(String host, int count)
        {
            this.host = host;
            this.count = count;
        }

        @Override
        public void run()
        {
            ZContext ctx = new ZContext();

            ZMQ.Socket receiver = ctx.createSocket(ZMQ.PULL);
            receiver.setImmediate(false);
            receiver.bind(host);

            idx = 0;
            while (idx < count) {
                if (idx % 5000 == 10) {
                    zmq.ZMQ.sleep(1);
                }
                ZMsg msg = ZMsg.recvMsg(receiver);
                msg.destroy();
                idx++;
            }

            // Clean up.
            ctx.destroySocket(receiver);
            ctx.close();

            finished.set(true);
        }
    }

    private class Client implements Runnable
    {
        private final String host;

        final AtomicBoolean finished = new AtomicBoolean();

        private final int count;

        public Client(String host, int count)
        {
            this.host = host;
            this.count = count;
        }

        @Override
        public void run()
        {
            ZContext ctx = new ZContext();

            ZMQ.Socket sender = ctx.createSocket(ZMQ.PUSH);
            sender.setImmediate(false);
            sender.connect(host);

            int idx = 0;
            while (idx++ < count) {
                ZMsg msg = new ZMsg();
                msg.add("DATA");
                boolean sent = msg.send(sender);
                assertThat(sent, is(true));
            }
            zmq.ZMQ.sleep(2);
            // Clean up.
            ctx.destroySocket(sender);
            ctx.close();

            finished.set(true);
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

    private void test(int count) throws IOException, InterruptedException
    {
        long start = System.currentTimeMillis();
        int port = Utils.findOpenPort();
        String host = "tcp://localhost:" + port;

        ExecutorService threadPool = Executors.newFixedThreadPool(2);

        Worker worker = new Worker(host, count);
        Client client = new Client(host, count);

        threadPool.submit(worker);
        threadPool.submit(client);

        threadPool.shutdown();
        threadPool.awaitTermination(20, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println("Worker received " + worker.idx + " messages");
        assertThat(worker.finished.get(), is(true));
        assertThat(client.finished.get(), is(true));
        System.out.println("Test done in " + (end - start) + " millis.");
    }
}
