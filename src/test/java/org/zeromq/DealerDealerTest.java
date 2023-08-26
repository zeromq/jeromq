package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Context;

public class DealerDealerTest
{
    private static final class Client implements Runnable
    {
        private final Context       context;
        private final boolean       verbose;
        private final String        host;
        private final int           messagesCount;
        private final Deque<String> queue;

        private int missed     = 0;
        private int reconnects = 0;
        private int count      = 0;
        private int received   = 0;

        private Client(Context context, boolean verbose, String host, int messagesCount, Deque<String> queue)
        {
            this.context = context;
            this.verbose = verbose;
            this.host = host;
            this.messagesCount = messagesCount;
            this.queue = queue;
        }

        @Override
        public void run()
        {
            ZMQ.Socket worker = context.socket(SocketType.DEALER);
            worker.connect(host);

            int msg = messagesCount;
            while (msg-- > 0) {
                String received = worker.recvStr();
                String expected = queue.pop();
                count++;

                while (!expected.equals(received)) {
                    count++;
                    missed++;
                    System.out.println("Missed " + expected + " (received " + received + ")");
                    expected = queue.pop();
                }

                this.received++;
                if (verbose) {
                    System.out.println("Received " + received);
                }

                if (count % 10 == 0) {
                    worker.disconnect(host);
                    reconnects++;
                    if (verbose) {
                        System.out.println("Reconnecting...");
                    }
                    worker.close();
                    worker = context.socket(SocketType.DEALER);
                    worker.connect(host);
                }

                if (count % 100 == 0) {
                    System.out.println(
                                       "Received: " + this.received + " missed: " + missed + " reconnects: "
                                               + reconnects);
                }
            }
            worker.close();
        }
    }

    @Test
    @Ignore
    public void testIssue335() throws InterruptedException, IOException
    {
        final boolean verbose = false;
        final int messagesCount = 1000;

        final ZMQ.Context context = ZMQ.context(1);
        final Deque<String> queue = new LinkedBlockingDeque<>();
        final String host = "tcp://localhost:" + Utils.findOpenPort();

        final Runnable server = () -> {
            final ZMQ.Socket server1 = context.socket(SocketType.DEALER);
            server1.bind(host);
            int msg = messagesCount;

            while (msg-- > 0) {
                final String payload = Integer.toString(msg);
                queue.add(payload);
                if (!server1.send(payload)) {
                    System.out.println("Send failed");
                }

                zmq.ZMQ.msleep(10);
            }
            server1.close();
        };

        final Client client = new Client(context, verbose, host, messagesCount, queue);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(server);
        executor.submit(client);

        long start = System.currentTimeMillis();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        System.out.println("Done in  " + (end - start) + " millis.");

        assertThat(client.missed, is(0));
        assertThat(client.reconnects, is(messagesCount / 10));
        assertThat(client.count, is(client.received));

        context.close();
    }
}
