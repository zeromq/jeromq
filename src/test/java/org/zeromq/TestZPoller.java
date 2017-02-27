package org.zeromq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller.EventsHandler;

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
        private final int port;
        private final Socket socket;
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
                    Assert.fail("unable to get server socket in writable state");
                }
            }
            socket.close();

            try {
                poller.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                Assert.fail("error while closing poller " + e.getMessage());
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
            Assert.assertNotNull("unable to receive msg after several cycles", msg);
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
        ZPoller poller = new ZPoller(new ZStar.VerySimpleSelectorCreator().create());

        SelectableChannel channel = null;
        Socket socket = null; // ctx.createSocket(ZMQ.SUB);

        boolean rc = false;
        rc = poller.register(socket, ZPoller.IN);
        Assert.assertFalse("Registering a null socket was successful", rc);
        rc = poller.register(channel, ZPoller.OUT);
        Assert.assertFalse("Registering a null channel was successful", rc);

        int events = poller.poll(10);
        Assert.assertEquals("reading event on without sockets", 0, events);

        rc = poller.isReadable(socket);
        Assert.assertFalse("checking read event on a null socket was successful", rc);
        rc = poller.writable(socket);
        Assert.assertFalse("checking write event on a null socket was successful", rc);

        rc = poller.readable(channel);
        Assert.assertFalse("checking read event on a null channel was successful", rc);
        rc = poller.isWritable(channel);
        Assert.assertFalse("checking write event on a null channel was successful", rc);

        EventsHandler global = null;

        poller.setGlobalHandler(global);

        EventsHandler handler = null;
        rc = poller.register(socket, handler, ZPoller.ERR);
        Assert.assertFalse("Register with handler on a null socket was successful", rc);
        rc = poller.register(channel, ZPoller.ERR);
        Assert.assertFalse("Register with handler on a null channel was successful", rc);

        events = poller.poll(10);
        Assert.assertEquals("reading event with events handlers without sockets", 0, events);

        poller.close();
    }
}
