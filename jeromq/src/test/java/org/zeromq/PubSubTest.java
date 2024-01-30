package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class PubSubTest
{
    @Test
    @Ignore
    public void testRaceConditionIssue322() throws IOException, InterruptedException
    {
        final ZMQ.Context context = ZMQ.context(1);
        final String address = "tcp://localhost:" + Utils.findOpenPort();
        final byte[] msg = "abc".getBytes();

        final int messagesNumber = 1000;
        //run publisher
        Runnable pub = () -> {
            Socket publisher = context.socket(SocketType.PUB);
            publisher.bind(address);
            int count = messagesNumber;
            while (count-- > 0) {
                publisher.send(msg);
                System.out.println("Send message " + count);
            }
            publisher.close();
        };
        //run subscriber
        Runnable sub = () -> {
            Socket subscriber = context.socket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            int count = messagesNumber;
            while (count-- > 0) {
                subscriber.recv();
                System.out.println("Received message " + count);
            }
            subscriber.close();
        };
        ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
            return thread;
        });
        executor.submit(sub);
        zmq.ZMQ.sleep(1);
        executor.submit(pub);

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        context.close();
    }

    @Test
    @Ignore
    public void testPubConnectSubBindIssue289and342() throws IOException
    {
        ZMQ.Context context = ZMQ.context(1);
        Socket pub = context.socket(SocketType.XPUB);
        assertThat(pub, notNullValue());

        Socket sub = context.socket(SocketType.SUB);
        assertThat(sub, notNullValue());
        boolean rc = sub.subscribe(new byte[0]);
        assertThat(rc, is(true));

        String host = "tcp://localhost:" + Utils.findOpenPort();

        rc = sub.bind(host);
        assertThat(rc, is(true));
        rc = pub.connect(host);
        assertThat(rc, is(true));

        zmq.ZMQ.msleep(300);

        rc = pub.send("test");
        assertThat(rc, is(true));

        assertThat(sub.recvStr(), is("test"));

        pub.close();
        sub.close();
        context.term();
    }

    @Test
    public void testUnsubscribeIssue554() throws Exception
    {
        final int port = Utils.findOpenPort();
        final ExecutorService service = Executors.newFixedThreadPool(2);

        final Callable<Boolean> pub = () -> {
            final ZMQ.Context ctx = ZMQ.context(1);
            assertThat(ctx, notNullValue());
            final Socket pubsocket = ctx.socket(SocketType.PUB);
            assertThat(pubsocket, notNullValue());

            boolean rc = pubsocket.bind("tcp://*:" + port);
            assertThat(rc, is(true));

            for (int idx = 1; idx <= 15; ++idx) {
                rc = pubsocket.sendMore("test/");
                assertThat(rc, is(true));
                rc = pubsocket.send("data" + idx);
                assertThat(rc, is(true));

                System.out.printf("Send-%d/", idx);
                ZMQ.msleep(100);
            }
            pubsocket.close();
            ctx.close();
            return true;
        };
        final Callable<Integer> sub = new Callable<Integer>()
        {
            @Override
            public Integer call()
            {
                final ZMQ.Context ctx = ZMQ.context(1);
                assertThat(ctx, notNullValue());
                final ZMQ.Socket sub = ctx.socket(SocketType.SUB);
                assertThat(sub, notNullValue());

                boolean rc = sub.setReceiveTimeOut(3000);
                assertThat(rc, is(true));

                rc = sub.subscribe("test/");
                assertThat(rc, is(true));

                rc = sub.connect("tcp://localhost:" + port);
                assertThat(rc, is(true));
                System.out.println("[SUB]");

                int received = receive(sub, 5);
                assertThat(received > 1, is(true));

                // unsubscribe from the topic and verify that we don't receive messages anymore
                rc = sub.unsubscribe("test/");
                assertThat(rc, is(true));
                System.out.printf("%n[UNSUB]%n");
                received = receive(sub, 10);

                sub.close();
                ctx.close();

                return received;
            }

            private int receive(ZMQ.Socket socket, int maxSeconds)
            {
                int received = 0;
                long current = System.currentTimeMillis();
                long end = current + maxSeconds * 1000;
                while (current < end) {
                    ZMsg msg = ZMsg.recvMsg(socket);
                    current = System.currentTimeMillis();
                    if (msg == null) {
                        continue;
                    }
                    ++received;
                }
                return received;
            }
        };
        final Future<Integer> rc = service.submit(sub);
        final Future<Boolean> pubf = service.submit(pub);
        service.shutdown();
        service.awaitTermination(60, TimeUnit.SECONDS);

        final int receivedAfterUnsubscription = rc.get();
        assertThat(receivedAfterUnsubscription, is(0));
        assertThat(pubf.get(), is(true));
    }
}
