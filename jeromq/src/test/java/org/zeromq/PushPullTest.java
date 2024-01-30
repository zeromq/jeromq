package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import zmq.util.Utils;

public class PushPullTest
{
    private static final int REPETITIONS = 10;

    static class Sender implements Runnable
    {
        private final CountDownLatch latch;
        private final int            port;

        public Sender(CountDownLatch latch, int port)
        {
            this.latch = latch;
            this.port = port;
        }

        @Override
        public void run()
        {
            String address = "tcp://*:" + port;

            ZMQ.Context context = ZMQ.context(1);
            assertThat(context, notNullValue());
            ZMQ.Socket socket = context.socket(SocketType.PUSH);
            assertThat(socket, notNullValue());

            // Socket options
            boolean rc = socket.setLinger(1000);
            assertThat(rc, is(true));

            rc = socket.bind(address);
            assertThat(rc, is(true));

            // Ensure that receiver is "connected" before sending
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int idx = 0; idx < REPETITIONS; ++idx) {
                rc = socket.send("data" + idx);
                assertThat(rc, is(true));
            }

            socket.close();
            context.close();
        }
    }

    static class Receiver implements Runnable
    {
        private final CountDownLatch latch;
        private final int            port;

        public Receiver(CountDownLatch latch, int port)
        {
            this.latch = latch;
            this.port = port;
        }

        @Override
        public void run()
        {
            String address = "tcp://localhost:" + port;

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

                ZMQ.msleep(10);
            }

            socket.close();
            context.close();
        }
    }

    @Test
    public void testIssue131() throws InterruptedException, IOException
    {
        CountDownLatch latch = new CountDownLatch(1);

        final int port = Utils.findOpenPort();

        Thread sender = new Thread(new Sender(latch, port));
        Thread receiver = new Thread(new Receiver(latch, port));

        // Start sender before receiver and use latch to ensure that receiver has connected
        // before sender is sending messages
        sender.start();
        receiver.start();

        sender.join();
        receiver.join();
    }
}
