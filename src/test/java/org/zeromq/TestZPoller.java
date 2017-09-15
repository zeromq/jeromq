package org.zeromq;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller.EventsHandler;
import org.zeromq.ZPoller.ItemCreator;
import org.zeromq.ZPoller.ItemHolder;

public class TestZPoller
{
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
            socket = context.createSocket(ZMQ.PUSH);
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

            try {
                poller.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                fail("error while closing poller " + e.getMessage());
            }
        }
    }

    @Test
    public void testPollerPollout() throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();

        final ZContext context = new ZContext();
        final ZPoller poller = new ZPoller(context.createSelector());
        final ZMQ.Socket receiver = context.createSocket(ZMQ.PULL);

        final Server client = new Server(context, port);
        client.start();

        try {
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
            int maxAttempts = 100;
            while (!Thread.currentThread().isInterrupted() && maxAttempts-- > 0) {
                int rc = poller.poll(1000);
                if (rc < 0) {
                    break;
                }
            }
            client.join();

            assertThat("unable to receive msg after several cycles", msg, notNullValue());
        }
        finally {
            receiver.close();
            context.close();
            poller.close();
        }
    }

    @Test
    public void testUseNull() throws IOException
    {
        Selector selector = new ZStar.VerySimpleSelectorCreator().create();
        ZPoller poller = new ZPoller(selector);

        SelectableChannel channel = null;
        Socket socket = null; // ctx.createSocket(ZMQ.SUB);

        boolean rc = false;
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
        selector.close();
    }

    @Test
    public void testZPollerNew() throws IOException
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

    @Test
    public void testGlobalHandler() throws IOException
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

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testItemEqualsBasic() throws IOException
    {
        ZContext ctx = new ZContext();

        ItemCreator itemCreator = new ZPoller.SimpleCreator();
        ZPoller poller = new ZPoller(itemCreator, ctx);

        try {
            Socket socket = ctx.createSocket(ZMQ.ROUTER);
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

    @Test
    public void testItemEquals() throws IOException
    {
        ZContext ctx = new ZContext();
        ItemCreator itemCreator = new ZPoller.SimpleCreator();
        ZPoller poller = new ZPoller(itemCreator, ctx);

        try {
            Socket socket = ctx.createSocket(ZMQ.ROUTER);
            ItemHolder holder = poller.create(socket, null, 0);

            ItemHolder other = new ZPoller.ZPollItem(socket, null, 0);
            assertThat(other, is(equalTo(holder)));

            other = new ZPoller.ZPollItem(socket, new EventsHandlerAdapter(), 0);
            assertThat(other, is(not(equalTo(holder))));

            socket = ctx.createSocket(ZMQ.ROUTER);
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

    @Test
    public void testReadable() throws IOException
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(ZMQ.XPUB);
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

    @Test
    public void testWritable() throws IOException
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(ZMQ.XPUB);
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

    @Test
    public void testError() throws IOException
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(ZMQ.XPUB);
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

    @Test
    public void testRegister() throws IOException
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(ZMQ.XPUB);
            ItemHolder holder = poller.create(socket, null, 0);
            boolean rc = poller.register(holder);
            assertThat(rc, is(true));
        }
        finally {
            poller.close();
            ctx.close();
        }
    }

    @Test
    public void testItems() throws IOException
    {
        ZContext ctx = new ZContext();
        ZPoller poller = new ZPoller(ctx);
        try {
            Socket socket = ctx.createSocket(ZMQ.XPUB);
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
}
