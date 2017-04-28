package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
        Runnable pub = new Runnable()
        {
            @Override
            public void run()
            {
                ZMQ.Socket publisher = context.socket(ZMQ.PUB);
                publisher.bind(address);
                int count = messagesNumber;
                while (count-- > 0) {
                    publisher.send(msg);
                    System.out.println("Send message " + count);
                }
                publisher.close();
            }

        };
        //run subscriber
        Runnable sub = new Runnable()
        {
            @Override
            public void run()
            {
                ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
                subscriber.connect(address);
                subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
                int count = messagesNumber;
                while (count-- > 0) {
                    subscriber.recv();
                    System.out.println("Received message " + count);
                }
                subscriber.close();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler()
                {
                    @Override
                    public void uncaughtException(Thread t, Throwable e)
                    {
                        e.printStackTrace();
                    }
                });
                return thread;
            }

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
        Socket pub = context.socket(ZMQ.XPUB);
        assertThat(pub, notNullValue());

        Socket sub = context.socket(ZMQ.SUB);
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
}
