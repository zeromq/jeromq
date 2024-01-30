package org.zeromq;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZProxy.Plug;
import zmq.util.Utils;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class XpubXsubZTest
{
    private AtomicReference<Throwable> testIssue476(final int front, final int back, final int max,
                                                    ExecutorService service, final ZContext ctx)
            throws InterruptedException, ExecutionException
    {
        final AtomicInteger numberReceived = new AtomicInteger(0);

        Future<?> subscriber = service.submit(() -> {
            Thread.currentThread().setName("Subscriber");
            try (ZContext ctx1 = new ZContext()) {
                final Socket requester = ctx1.createSocket(SocketType.SUB);
                requester.connect("tcp://localhost:" + back);
                requester.subscribe("hello".getBytes(ZMQ.CHARSET));

                while (numberReceived.get() < max) {
                    ZMsg.recvMsg(requester);
                    numberReceived.incrementAndGet();
                }
            }
        });

        final AtomicReference<Throwable> error = new AtomicReference<>();
        service.submit(() -> {
            Thread.currentThread().setName("Publisher");
            try {
                Socket pub = ctx.createSocket(SocketType.PUB);
                pub.connect("tcp://localhost:" + front);
                while (numberReceived.get() < max) {
                    ZMsg message = ZMsg.newStringMsg("hello", "world");
                    boolean rc = message.send(pub);
                    assertThat(rc, is(true));
                    ZMQ.msleep(5);
                }
            }
            catch (Throwable ex) {
                error.set(ex);
                ex.printStackTrace();
            }
        });

        try {
            subscriber.get(5, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            System.err.println("Timeout waiting for subscriber to get " + max + " messages.");
            error.set(e);
            e.printStackTrace();

            numberReceived.set(max + 1); // Make sure the threads will finish
        }

        ZMQ.msleep(300);
        return error;
    }

    @Test
    public void testIssue476() throws InterruptedException, IOException, ExecutionException
    {
        final int front = Utils.findOpenPort();
        final int back = Utils.findOpenPort();

        final int max = 20;

        ExecutorService service = Executors.newFixedThreadPool(3);
        try (final ZContext ctx = new ZContext()) {
            service.submit(() -> {
                Thread.currentThread().setName("Proxy");
                Socket xpub = ctx.createSocket(SocketType.XPUB);
                xpub.bind("tcp://*:" + back);
                Socket xsub = ctx.createSocket(SocketType.XSUB);
                xsub.bind("tcp://*:" + front);
                Socket ctrl = ctx.createSocket(SocketType.PAIR);
                ctrl.bind("inproc://ctrl-proxy");
                ZMQ.proxy(xpub, xsub, null, ctrl);
            });
            final AtomicReference<Throwable> error = testIssue476(front, back, max, service, ctx);
            ZMQ.Socket ctrl = ctx.createSocket(SocketType.PAIR);
            ctrl.connect("inproc://ctrl-proxy");
            ctrl.send(ZMQ.PROXY_TERMINATE);
            ctrl.close();

            service.shutdown();
            service.awaitTermination(2, TimeUnit.SECONDS);

            assertThat(error.get(), nullValue());
        }
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
                    return ctx.createSocket(SocketType.XSUB);
                }
                if (place == Plug.BACK) {
                    return ctx.createSocket(SocketType.XPUB);
                }
                if (place == Plug.CAPTURE) {
                    return ctx.createSocket(SocketType.PUB);
                }
                return null;
            }

            @Override
            public boolean configure(Socket socket, Plug place, Object... args)
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
        try (final ZContext ctx = new ZContext()) {
            ZProxy.Proxy actor = new ZProxy.Proxy.SimpleProxy()
            {
                @Override
                public Socket create(ZContext ctx, Plug place, Object... args)
                {
                    if (place == Plug.FRONT) {
                        return ctx.createSocket(SocketType.XSUB);
                    }
                    if (place == Plug.BACK) {
                        return ctx.createSocket(SocketType.XPUB);
                    }
                    if (place == Plug.CAPTURE) {
                        return ctx.createSocket(SocketType.PUB);
                    }
                    return null;
                }

                @Override
                public boolean configure(Socket socket, Plug place, Object... args)
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
        }
    }
}
