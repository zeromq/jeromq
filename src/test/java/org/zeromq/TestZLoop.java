package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZLoop.IZLoopHandler;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class TestZLoop
{
    private String   received;
    private ZContext ctx;
    private Socket   input;
    private Socket   output;

    @Before
    public void setUp()
    {
        ctx = new ZContext();
        assert (ctx != null);

        output = ctx.createSocket(SocketType.PAIR);
        assert (output != null);
        output.bind("inproc://zloop.test");
        input = ctx.createSocket(SocketType.PAIR);
        assert (input != null);
        input.connect("inproc://zloop.test");

        received = "FAILED";
    }

    @After
    public void tearDown()
    {
        ctx.destroy();
    }

    @Ignore
    public void testZLoopWithUDP() throws IOException
    {
        final int port = Utils.findOpenPort();
        final InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);

        ZLoop loop = new ZLoop(ctx);

        DatagramChannel udpIn = DatagramChannel.open();
        assertThat(udpIn, notNullValue());
        udpIn.configureBlocking(false);
        udpIn.socket().bind(new InetSocketAddress(port));

        DatagramChannel udpOut = DatagramChannel.open();
        assertThat(udpOut, notNullValue());
        udpOut.configureBlocking(false);
        udpOut.socket().connect(addr);

        final AtomicInteger counter = new AtomicInteger();
        final AtomicBoolean done = new AtomicBoolean();

        loop.addPoller(new PollItem(udpIn, ZMQ.Poller.POLLIN), (loop12, item, arg) -> {
            DatagramChannel udpIn1 = (DatagramChannel) arg;
            ByteBuffer bb = ByteBuffer.allocate(3);
            try {
                udpIn1.receive(bb);
                String read = new String(bb.array(), 0, bb.limit(), ZMQ.CHARSET);
                assertThat(read, is("udp"));
                done.set(true);
                counter.incrementAndGet();
            }
            catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            return -1;
        }, udpIn);
        loop.addPoller(new PollItem(udpOut, ZMQ.Poller.POLLOUT), (loop1, item, arg) -> {
            DatagramChannel udpOut1 = (DatagramChannel) arg;
            try {
                ByteBuffer bb = ByteBuffer.allocate(3);
                bb.put("udp".getBytes(ZMQ.CHARSET));
                bb.flip();
                int written = udpOut1.send(bb, addr);
                assertThat(written, is(3));
                counter.incrementAndGet();
            }
            catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            return 0;
        }, udpOut);
        loop.start();

        assertThat(done.get(), is(true));
        assertThat(counter.get(), is(2));

        udpIn.close();
        udpOut.close();
    }

    @Test
    public void testZLoop()
    {
        int rc;

        // setUp() should create the context
        assert (ctx != null);

        ZLoop loop = new ZLoop(ctx);
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = (loop12, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop1, item, arg) -> {
            received = ((Socket) arg).recvStr(0);
            //  Just end the reactor
            return -1;
        };

        //  After 10 msecs, send a ping message to output
        loop.addTimer(10, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);
        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }

    @Test
    public void testZLoopAddTimerFromTimer()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            final long now = System.currentTimeMillis();

            IZLoopHandler timerEvent2 = (loop12, item1, arg1) -> {
                final long now2 = System.currentTimeMillis();
                assert (now2 >= now + 10);
                ((Socket) arg1).send("PING", 0);
                return 0;
            };
            loop13.addTimer(10, 1, timerEvent2, arg);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop1, item, arg) -> {
            received = ((Socket) arg).recvStr(0);
            //  Just end the reactor
            return -1;
        };

        //  After 10 msecs, fire a timer that registers
        //  another timer that sends the ping message
        loop.addTimer(10, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);

        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);

    }

    @Test(timeout = 1000)
    public void testZLoopAddTimerFromSocketHandler()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop12, item, arg) -> {
            final long now = System.currentTimeMillis();
            IZLoopHandler timerEvent2 = (loop1, item1, arg1) -> {
                final long now2 = System.currentTimeMillis();
                Assert.assertTrue(now2 >= now + 10);
                received = ((Socket) arg1).recvStr(0);
                //  Just end the reactor
                return -1;
            };
            //  After 10 msec fire a timer that ends the reactor
            loop12.addTimer(10, 1, timerEvent2, arg);
            return 0;
        };

        //  Fire a timer that sends the ping message
        loop.addTimer(0, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);

        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }

    @Test(timeout = 1000)
    public void testZLoopEndReactorFromTimer()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop12, item, arg) -> {
            //  After 10 msecs, fire an event that ends the reactor
            IZLoopHandler shutdownEvent = (loop1, item1, arg1) -> {
                received = ((Socket) arg1).recvStr(0);
                //  Just end the reactor
                return -1;
            };
            loop12.addTimer(10, 1, shutdownEvent, arg);
            return 0;
        };

        //  Fire event that sends a ping message to output
        loop.addTimer(0, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);
        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }
}
