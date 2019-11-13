package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

public class PushPullTest
{
    private static final int REPETITIONS = 10;

    static class Sender implements Runnable
    {
        private final CountDownLatch latch;
        private ZMQ.Context context;
        private ZMQ.Socket socket;

        Sender(CountDownLatch latch)
        {
            this.latch = latch;
        }

        private String init()
        {
            context = ZMQ.context(1);
            assertThat(context, notNullValue());
            socket = context.socket(SocketType.PUSH);
            assertThat(socket, notNullValue());

            // Socket options
            boolean rc = socket.setLinger(1000);
            assertThat(rc, is(true));

            rc = socket.bind("tcp://*:*");
            assertThat(rc, is(true));

            return socket.getLastEndpoint();
        }

        @Override
        public void run()
        {
            // Ensure that receiver is "connected" before sending
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int idx = 0; idx < REPETITIONS; ++idx) {
                boolean rc = socket.send("data" + idx);
                assertThat(rc, is(true));
            }

            socket.close();
            context.close();
        }
    }

    static class Receiver implements Runnable
    {
        private final CountDownLatch latch;
        private final String address;

        Receiver(CountDownLatch latch, String address)
        {
            this.latch = latch;
            this.address = address;
        }

        @Override
        public void run()
        {
            ZMQ.Context context = ZMQ.context(1);
            assertThat(context, notNullValue());
            ZMQ.Socket socket = context.socket(SocketType.PULL);
            assertThat(socket, notNullValue());

            // Options Section
            boolean rc = socket.setRcvHWM(1);
            assertThat(rc, is(true));

            rc = socket.connect(address);
            assertThat(rc, is(true));

            latch.countDown();

            for (int idx = 0; idx < REPETITIONS; ++idx) {
                String recvd = socket.recvStr();
                assertThat(recvd, is("data" + idx));
            }

            socket.close();
            context.close();
        }
    }

    @Test
    public void testIssue131() throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);

        Sender target = new Sender(latch);
        String address = target.init();
        Thread sender = new Thread(target);
        Thread receiver = new Thread(new Receiver(latch, address));

        // Start sender before receiver and use latch to ensure that receiver has connected
        // before sender is sending messages
        sender.start();
        receiver.start();

        sender.join();
        receiver.join();
    }

    @Test
    public void testConnection()
    {
        try (ZContext ctx = new ZContext()) {
            ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
            final int port = server.bindToRandomPort("tcp://*");

            //  Create and connect client socket
            ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
            boolean rc = client.connect("tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Send a single message from server to client
            rc = server.send("Hello");
            assertThat(rc, is(true));

            String msg = client.recvStr();
            assertThat(msg, is("Hello"));
        }
    }
}
