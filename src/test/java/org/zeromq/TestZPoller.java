package org.zeromq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller.EventsHandler;

public class TestZPoller
{
    @Test
    public void testUseNull() throws IOException
    {
        ZContext ctx = null; //new ZContext();
        ZPoller poller = new ZPoller(new ZStar.VerySimpleSelectorCreator().create());

        SelectableChannel channel = null;
        Socket socket = null; //ctx.createSocket(ZMQ.SUB);

        boolean rc = false;
        rc = poller.register(socket, ZPoller.IN);
        Assert.assertFalse("Registering a null socket was successful",  rc);
        rc = poller.register(channel, ZPoller.OUT);
        Assert.assertFalse("Registering a null channel was successful",  rc);

        int events = poller.poll(10);
        Assert.assertEquals("reading event on without sockets",  0, events);

        rc = poller.isReadable(socket);
        Assert.assertFalse("checking read event on a null socket was successful",  rc);
        rc = poller.writable(socket);
        Assert.assertFalse("checking write event on a null socket was successful",  rc);

        rc = poller.readable(channel);
        Assert.assertFalse("checking read event on a null channel was successful",  rc);
        rc = poller.isWritable(channel);
        Assert.assertFalse("checking write event on a null channel was successful",  rc);

        EventsHandler global = null;

        poller.setGlobalHandler(global);

        EventsHandler handler = null;
        rc = poller.register(socket, handler, ZPoller.ERR);
        Assert.assertFalse("Register with handler on a null socket was successful",  rc);
        rc = poller.register(channel, ZPoller.ERR);
        Assert.assertFalse("Register with handler on a null channel was successful",  rc);

        events = poller.poll(10);
        Assert.assertEquals("reading event with events handlers without sockets",  0, events);

        poller.close();
        System.out.println();
    }
}
