package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import zmq.util.Utils;

public class XpubXsubZTest
{
    @Test
    public void testIssue476() throws InterruptedException, IOException, ExecutionException
    {
        final int front = Utils.findOpenPort();
        final int back = Utils.findOpenPort();

        final int max = 10;

        ExecutorService service = Executors.newFixedThreadPool(3);
        final ZContext ctx = new ZContext();
        service.submit(new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("Proxy");
                ZMQ.Socket xpub = ctx.createSocket(ZMQ.XPUB);
                xpub.bind("tcp://*:" + back);
                ZMQ.Socket xsub = ctx.createSocket(ZMQ.XSUB);
                xsub.bind("tcp://*:" + front);
                ZMQ.Socket ctrl = ctx.createSocket(ZMQ.PAIR);
                ctrl.bind("inproc://ctrl-proxy");
                ZMQ.proxy(xpub, xsub, null, ctrl);
            }
        });
        Future<?> subscriber = service.submit(new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("Subscriber");
                final ZContext ctx = new ZContext();
                try {
                    final ZMQ.Socket requester = ctx.createSocket(ZMQ.SUB);
                    requester.connect("tcp://localhost:" + back);
                    requester.subscribe("hello".getBytes(ZMQ.CHARSET));
                    int count = 0;
                    while (count < max) {
                        ZMsg.recvMsg(requester);
                        count++;
                    }
                }
                finally {
                    ctx.close();
                }
            }
        });

        final AtomicReference<Throwable> error = new AtomicReference<>();
        service.submit(new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("Publisher");
                try {
                    ZMQ.Socket pub = ctx.createSocket(ZMQ.PUB);
                    pub.connect("tcp://localhost:" + front);
                    int count = 0;
                    while (++count < max * 4) {
                        ZMsg message = ZMsg.newStringMsg("hello", "world");
                        boolean rc = message.send(pub);
                        assertThat(rc, is(true));
                        ZMQ.msleep(10);
                    }
                }
                catch (Throwable ex) {
                    error.set(ex);
                    ex.printStackTrace();
                }
            }
        });
        subscriber.get();

        ZMQ.msleep(300);
        ZMQ.Socket ctrl = ctx.createSocket(ZMQ.PAIR);
        ctrl.connect("inproc://ctrl-proxy");
        ctrl.send("TERMINATE");
        ctrl.close();

        service.shutdown();
        service.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(error.get(), nullValue());

        ctx.close();
    }
}
