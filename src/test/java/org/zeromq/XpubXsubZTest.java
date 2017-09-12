package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZProxy.Plug;

import zmq.util.Utils;

public class XpubXsubZTest
{
    private AtomicReference<Throwable> testIssue476(final int front, final int back, final int max,
                                                    ExecutorService service, final ZContext ctx)
            throws InterruptedException, ExecutionException
    {
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
        return error;
    }

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
        final AtomicReference<Throwable> error = testIssue476(front, back, max, service, ctx);
        ZMQ.Socket ctrl = ctx.createSocket(ZMQ.PAIR);
        ctrl.connect("inproc://ctrl-proxy");
        ctrl.send(ZMQ.PROXY_TERMINATE);
        ctrl.close();

        service.shutdown();
        service.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(error.get(), nullValue());

        ctx.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testIssue476WithZProxy() throws InterruptedException, IOException, ExecutionException
    {
        final int front = Utils.findOpenPort();
        final int back = Utils.findOpenPort();

        final int max = 10;

        ExecutorService service = Executors.newFixedThreadPool(3);
        final ZContext ctx = new ZContext();

        ZProxy.Proxy actor = new ZProxy.Proxy.SimpleProxy()
        {
            @Override
            public Socket create(ZContext ctx, Plug place, Object... args)
            {
                if (place == Plug.FRONT) {
                    return ctx.createSocket(ZMQ.XSUB);
                }
                if (place == Plug.BACK) {
                    return ctx.createSocket(ZMQ.XPUB);
                }
                if (place == Plug.CAPTURE) {
                    return ctx.createSocket(ZMQ.PUB);
                }
                return null;
            }

            @Override
            public boolean configure(Socket socket, Plug place, Object... args) throws IOException
            {
                if (place == Plug.FRONT) {
                    return socket.bind("tcp://*:" + front);
                }
                if (place == Plug.BACK) {
                    return socket.bind("tcp://*:" + back);
                }
                return true;
            }
        };
        ZProxy proxy = ZProxy.newZProxy(ctx, null, actor, UUID.randomUUID().toString());
        proxy.start(true);
        final AtomicReference<Throwable> error = testIssue476(front, back, max, service, ctx);

        proxy.exit(false);

        service.shutdown();
        service.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(error.get(), nullValue());

        ctx.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testIssue476WithZProxyZmqPump() throws InterruptedException, IOException, ExecutionException
    {
        final int front = Utils.findOpenPort();
        final int back = Utils.findOpenPort();

        final int max = 10;

        ExecutorService service = Executors.newFixedThreadPool(3);
        final ZContext ctx = new ZContext();

        ZProxy.Proxy actor = new ZProxy.Proxy.SimpleProxy()
        {
            @Override
            public Socket create(ZContext ctx, Plug place, Object... args)
            {
                if (place == Plug.FRONT) {
                    return ctx.createSocket(ZMQ.XSUB);
                }
                if (place == Plug.BACK) {
                    return ctx.createSocket(ZMQ.XPUB);
                }
                if (place == Plug.CAPTURE) {
                    return ctx.createSocket(ZMQ.PUB);
                }
                return null;
            }

            @Override
            public boolean configure(Socket socket, Plug place, Object... args) throws IOException
            {
                if (place == Plug.FRONT) {
                    return socket.bind("tcp://*:" + front);
                }
                if (place == Plug.BACK) {
                    return socket.bind("tcp://*:" + back);
                }
                return true;
            }
        };
        ZProxy proxy = ZProxy.newProxy(ctx, "XPub-XSub", actor, UUID.randomUUID().toString());
        proxy.start(true);

        final AtomicReference<Throwable> error = testIssue476(front, back, max, service, ctx);
        proxy.exit(false);

        service.shutdown();
        service.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(error.get(), nullValue());

        ctx.close();
    }
}
