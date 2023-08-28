package org.zeromq;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller.EventsHandler;
import org.zeromq.ZPoller.ItemCreator;
import org.zeromq.ZPoller.ItemHolder;

import zmq.ZError;
import zmq.util.function.BiFunction;

public class TestZPoller
{
    private static final class EventsHandlerCounter implements BiFunction<SelectableChannel, Integer, Boolean>
    {
        private final AtomicInteger count;

        private EventsHandlerCounter(AtomicInteger count)
        {
            this.count = count;
        }

        @Override
        public Boolean apply(SelectableChannel channel, Integer events)
        {
            assertThat((events & ZPoller.IN) > 0, is(true));
            try {
                Pipe.SourceChannel sc = (Pipe.SourceChannel) channel;
                sc.read(ByteBuffer.allocate(5));
                count.incrementAndGet();
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
    }

    private static final class EventsHandlerErrorCounter implements BiFunction<SelectableChannel, Integer, Boolean>
    {
        private final AtomicInteger error;

        private EventsHandlerErrorCounter(AtomicInteger error)
        {
            this.error = error;
        }

        @Override
        public Boolean apply(SelectableChannel channel, Integer events)
        {
            error.incrementAndGet();
            return false;
        }
    }

    private static class EventsHandlerAdapter implements EventsHandler
    {
        @Override
        public boolean events(SelectableChannel channel, int events)
        {
            return false;
        }

        @Override
        public boolean events(Socket socket, int events)
        {
            return false;
        }
    }

    static class Server extends Thread
    {
        private final int     port;
        private final Socket  socket;
        private final ZPoller poller;

        public Server(ZContext context, int port)
        {
            this.port = port;
            socket = context.createSocket(SocketType.PUSH);
            poller = new ZPoller(context);
        }

        @Override
        public void run()
        {
            socket.bind("tcp://127.0.0.1:" + port);

            poller.register(socket, ZPoller.WRITABLE);
            while (!Thread.currentThread().isInterrupted()) {
                poller.poll(-1);
                if (poller.isWritable(socket)) {
                    ZMsg msg = new ZMsg();
                    msg.add("OK");
                    msg.send(socket);

                    break;
                }
                else {
                    fail("unable to get server socket in writable state");
                }
            }
            socket.close();

            poller.close();
        }
    }

    @Test(timeout = 5000)
    public void testPollerPollout() throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();

        try (ZContext context = new ZContext();
             ZPoller poller = new ZPoller(context);
             ZMQ.Socket receiver = context.createSocket(SocketType.PULL)) {
            context.setLinger(5000);
            final Server client = new Server(context, port);
            client.start();
            receiver.connect("tcp://127.0.0.1:" + port);

            final AtomicReference<ZMsg> msg = new AtomicReference<>();
            poller.register(receiver, new EventsHandlerAdapter()
            {
                @Override
                public boolean events(Socket s, int events)
                {
                    if (receiver.equals(s)) {
                        msg.set(ZMsg.recvMsg(receiver));
                        return false;
                    }
                    else {
                        return true;
                    }
                }
            }, ZPoller.IN);
            int maxAttempts = 5;
            while (!Thread.currentThread().isInterrupted() && maxAttempts-- > 0) {
                int rc = poller.poll(1000);
                if (rc < 0) {
                    break;
                }
            }
            client.join();

            assertThat("unable to receive msg after several cycles", msg, notNullValue());
        }
    }

    @Test(timeout = 5000)
    public void testUseNull()
    {
        final ZContext context = new ZContext();

        ZPoller poller = new ZPoller(context);

        SelectableChannel channel = null;
        Socket socket = null; // ctx.createSocket(ZMQ.SUB);

        boolean rc;
        rc = poller.register(socket, ZPoller.IN);
        assertThat("Registering a null socket was successful", rc, is(false));
        rc = poller.register(channel, ZPoller.OUT);
        assertThat("Registering a null channel was successful", rc, is(false));

        int events = poller.poll(10);
        assertThat("reading event on without sockets", events, is(0));

        rc = poller.isReadable(socket);
        assertThat("checking read event on a null socket was successful", rc, is(false));
        rc = poller.writable(socket);
        assertThat("checking write event on a null socket was successful", rc, is(false));

        rc = poller.readable(channel);
        assertThat("checking read event on a null channel was successful", rc, is(false));
        rc = poller.isWritable(channel);
        assertThat("checking write event on a null channel was successful", rc, is(false));

        EventsHandler global = null;

        poller.setGlobalHandler(global);

        EventsHandler handler = null;
        rc = poller.register(socket, handler, ZPoller.ERR);
        assertThat("Register with handler on a null socket was successful", rc, is(false));
        rc = poller.register(channel, ZPoller.ERR);
        assertThat("Register with handler on a null channel was successful", rc, is(false));

        events = poller.poll(10);
        assertThat("reading event with events handlers without sockets", events, is(0));

        poller.close();
        context.close();
    }

    @Test(timeout = 5000)
    public void testZPollerNew()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            ZPoller other = new ZPoller(poller);
            other.close();

            ItemCreator itemCreator = new ZPoller.SimpleCreator();
            other = new ZPoller(itemCreator, poller);
            other.close();
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testGlobalHandler()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            assertThat(poller.getGlobalHandler(), nullValue());
            EventsHandler handler = new EventsHandlerAdapter();
            poller.setGlobalHandler(handler);
            assertThat(poller.getGlobalHandler(), is(handler));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testItemEqualsBasic()
    {
        ZContext ctx = new ZContext();

        ItemCreator itemCreator = new ZPoller.SimpleCreator();
        ZPoller poller = new ZPoller(itemCreator, ctx);

        try {
            Socket socket = ctx.createSocket(SocketType.ROUTER);
            ItemHolder holder = itemCreator.create(socket, null, 0);

            assertThat(holder, is(holder));
            assertThat(holder, is(not(equalTo(null))));

            assertThat(holder.equals(""), is(false));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testItemEquals()
    {
        ZContext ctx = new ZContext();
        ItemCreator itemCreator = new ZPoller.SimpleCreator();
        ZPoller poller = new ZPoller(itemCreator, ctx);

        try {
            Socket socket = ctx.createSocket(SocketType.ROUTER);
            ItemHolder holder = poller.create(socket, null, 0);

            ItemHolder other = new ZPoller.ZPollItem(socket, null, 0);
            assertThat(other, is(equalTo(holder)));

            other = new ZPoller.ZPollItem(socket, new EventsHandlerAdapter(), 0);
            assertThat(other, is(not(equalTo(holder))));

            socket = ctx.createSocket(SocketType.ROUTER);
            other = new ZPoller.ZPollItem(socket, null, 0);
            assertThat(other, is(not(equalTo(holder))));

            other = itemCreator.create((SelectableChannel) null, null, 0);
            assertThat(other, is(not(equalTo(holder))));

            holder = poller.create(socket, new EventsHandlerAdapter(), 0);
            other = new ZPoller.ZPollItem(socket, new EventsHandlerAdapter(), 0);
            assertThat(other, is(not(equalTo(holder))));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testReadable()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(SocketType.XPUB);
            poller.register(socket, new EventsHandlerAdapter());

            boolean rc = poller.readable(socket);
            assertThat(rc, is(false));

            rc = poller.isReadable(socket);
            assertThat(rc, is(false));

            rc = poller.pollin(socket);
            assertThat(rc, is(false));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testWritable()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(SocketType.XPUB);
            poller.register(socket, ZPoller.OUT);

            boolean rc = poller.writable(socket);
            assertThat(rc, is(false));

            rc = poller.isWritable(socket);
            assertThat(rc, is(false));

            rc = poller.pollout(socket);
            assertThat(rc, is(false));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testError()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(SocketType.XPUB);
            poller.register(socket, ZPoller.ERR);

            boolean rc = poller.error(socket);
            assertThat(rc, is(false));

            rc = poller.isError(socket);
            assertThat(rc, is(false));

            rc = poller.pollerr(socket);
            assertThat(rc, is(false));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testRegister()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(SocketType.XPUB);
            ItemHolder holder = poller.create(socket, null, 0);
            boolean rc = poller.register(holder);
            assertThat(rc, is(true));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testItems()
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(SocketType.XPUB);
            Iterable<ItemHolder> items = poller.items(null);
            assertThat(items, notNullValue());

            ItemHolder holder = poller.create(socket, null, 0);
            poller.register(holder);
            items = poller.items(socket);
            assertThat(items, hasItem(holder));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testMultipleRegistrations() throws IOException
    {
        Selector selector = Selector.open();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();

        BiFunction<SelectableChannel, Integer, Boolean> cb1 = new EventsHandlerCounter(count);
        BiFunction<SelectableChannel, Integer, Boolean> cb2 = new EventsHandlerCounter(count);
        BiFunction<SelectableChannel, Integer, Boolean> cberr = new EventsHandlerErrorCounter(error);
        Pipe[] pipes = new Pipe[2];
        try (
                ZPoller poller = new ZPoller(selector)) {
            pipes[0] = pipe(poller, cberr, cb1, cb2);
            pipes[1] = pipe(poller, cberr, cb1, cb2);
            int max = 10;
            do {
                poller.poll(100);
                --max;
            } while (count.get() != 4 && max > 0);

            assertThat("Not all events handlers checked", error.get(), is(equalTo(0)));
            if (max == 0 && count.get() != 4) {
                fail("Unable to poll after 10 attempts");
            }
        }
        finally {
            selector.close();
            for (Pipe pipe : pipes) {
                pipe.sink().close();
                pipe.source().close();
            }
        }
    }

    @SafeVarargs
    private final Pipe pipe(ZPoller poller, BiFunction<SelectableChannel, Integer, Boolean> errors,
                            BiFunction<SelectableChannel, Integer, Boolean>... ins)
                                    throws IOException
    {
        Pipe pipe = Pipe.open();
        Pipe.SourceChannel source = pipe.source();
        source.configureBlocking(false);
        for (BiFunction<SelectableChannel, Integer, Boolean> handler : ins) {
            poller.register(source, handler, ZPoller.IN);
        }
        poller.register(source, errors, ZPoller.ERR);

        pipe.sink().write(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));

        return pipe;
    }

    @Test(timeout = 5000)
    public void testIssue729() throws IOException
    {
        int port = Utils.findOpenPort();
        ZContext ctx = new ZContext();
        Socket sub = ctx.createSocket(SocketType.SUB);
        Socket pub = ctx.createSocket(SocketType.PUB);
        sub.bind("tcp://127.0.0.1:" + port);
        pub.connect("tcp://127.0.0.1:" + port);
        boolean subscribe = sub.subscribe("");
        assertTrue("SUB Socket could not subscribe", subscribe);
        ZPoller zPoller = new ZPoller(ctx);

        try {
            zPoller.register(new ZPoller.ZPollItem(sub, null, ZPoller.POLLIN));

            Thread server = new Thread(() -> {
                while (true) {
                    try {
                        pub.send("hello");
                        Thread.sleep(100);
                    }
                    catch (InterruptedException ignored) {
                    }
                    catch (ZMQException exc) {
                        assertThat(exc.getErrorCode(), is(ZError.ETERM));
                    }
                }
            });
            server.start();
            int rc = zPoller.poll(-1);
            server.interrupt();
            assertThat("ZPoller does not understand SUB socket signaled", rc, is(1));
            boolean pollin = zPoller.pollin(sub);
            assertTrue(pollin);
            String hello = sub.recvStr();
            assertThat("recieved message are not identical to what has been sent", hello, is("hello"));
        }
        finally {
            zPoller.close();
            ctx.close();
        }
    }
}
