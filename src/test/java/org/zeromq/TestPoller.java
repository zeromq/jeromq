package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestPoller
{
    static class Client implements Runnable
    {
        private final AtomicBoolean received = new AtomicBoolean();
        private final String        address;

        public Client(String addr)
        {
            this.address = addr;
        }

        @Override
        public void run()
        {
            ZMQ.Context context = ZMQ.context(1);
            Socket pullConnect = context.socket(ZMQ.PULL);

            pullConnect.connect(address);

            System.out.println("Receiver Started");
            pullConnect.recv(0);
            received.set(true);

            pullConnect.close();
            context.close();
            System.out.println("Receiver Stopped");
        }
    }

    @Test
    public void testPollerPollout() throws Exception
    {
        int port = Utils.findOpenPort();
        String addr = "tcp://127.0.0.1:" + port;

        Client client = new Client(addr);

        ZMQ.Context context = ZMQ.context(1);

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket(ZMQ.PUSH);
        sender.setImmediate(false);
        sender.bind(addr);

        ZMQ.Poller outItems;
        outItems = context.poller();
        outItems.register(sender, ZMQ.Poller.POLLOUT);

        ExecutorService executor = Executors.newSingleThreadExecutor();
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

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        sender.close();
        System.out.println("Poller test done");
        assertThat(client.received.get(), is(true));
        context.term();
    }
}
