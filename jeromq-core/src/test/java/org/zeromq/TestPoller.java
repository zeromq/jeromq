package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestPoller
{
    static class Client implements Runnable
    {
        private final AtomicBoolean  received = new AtomicBoolean();
        private final String         address;
        private final CountDownLatch ready = new CountDownLatch(0);

        public Client(String addr)
        {
            this.address = addr;
        }

        @Override
        public void run()
        {
            ZMQ.Context context = ZMQ.context(1);
            Socket pullConnect = context.socket(SocketType.PULL);

            pullConnect.connect(address);

            System.out.println("Receiver Started");
            pullConnect.recv(0);
            received.set(true);
            try {
                ready.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            pullConnect.close();
            context.close();
            System.out.println("Receiver Stopped");
        }
    }

    @Test(timeout = 5000)
    public void testPollerPollout() throws Exception
    {
        int port = Utils.findOpenPort();
        String addr = "tcp://127.0.0.1:" + port;

        Client client = new Client(addr);

        ZMQ.Context context = ZMQ.context(1);

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket(SocketType.PUSH);
        sender.setImmediate(false);
        sender.bind(addr);

        ZMQ.Poller outItems = context.poller();
        outItems.register(sender, ZMQ.Poller.POLLOUT);

        ExecutorService executor;
        try {
            executor = Executors.newSingleThreadExecutor();
            while (!Thread.currentThread().isInterrupted()) {
                outItems.poll(1000);
                if (outItems.pollout(0)) {
                    boolean rc = sender.send("OK", 0);
                    assertThat(rc, is(true));
                    System.out.println("Sender: wrote message");
                    break;
                }
                else {
                    System.out.println("Sender: not writable");
                    executor.submit(client);
                }
            }
        }
        finally {
            client.ready.countDown();
        }
        executor.shutdown();
        executor.awaitTermination(4, TimeUnit.SECONDS);
        outItems.close();
        sender.close();
        System.out.println("Poller test done");
        assertThat(client.received.get(), is(true));
        context.term();
    }

    @Test(timeout = 5000)
    public void testExitPollerIssue580() throws InterruptedException, ExecutionException
    {
        Future<Integer> future;

        ExecutorService service = Executors.newSingleThreadExecutor();
        try (
            ZContext context = new ZContext(1);
            ZMQ.Poller poller = context.createPoller(1)) {
            ZMQ.Socket socket = context.createSocket(SocketType.PAIR);
            assertThat(socket, notNullValue());

            int rc = poller.register(socket, ZMQ.Poller.POLLIN);
            assertThat(rc, is(0));

            future = service.submit(() -> poller.poll(-1));
            assertThat(future, notNullValue());

            ZMQ.msleep(100);
        }

        service.shutdown();
        service.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(future.get(), is(-1));
    }
}
