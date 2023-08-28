package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.After;
//import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class ZLoopTest
{
    private String   received;
    private ZContext ctx;
    private Socket   input;
    private Socket   output;

    @Before
    public void setUp()
    {
        ctx = new ZContext();
        assertThat(ctx, notNullValue());

        output = ctx.createSocket(SocketType.PAIR);
        assertThat(output, notNullValue());
        output.bind("inproc://zloop.test");
        input = ctx.createSocket(SocketType.PAIR);
        assertThat(input, notNullValue());
        input.connect("inproc://zloop.test");

        received = "FAILED";
    }

    @After
    public void tearDown()
    {
        ctx.destroy();
    }

    @Test
    public void testZLoop()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assertThat(loop, notNullValue());

        ZLoop.IZLoopHandler timerEvent = (loop12, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop1, item, arg) -> {
            received = ((Socket) arg).recvStr(0);
            // Just end the reactor
            return -1;
        };

        // After 10 msecs, send a ping message to output
        loop.addTimer(10, 1, timerEvent, input);

        // When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        assertThat(rc, is(0));
        loop.start();

        loop.removePoller(pollInput);
        assertThat(received, is("PING"));
    }

    @Test
    public void testZLoopAddTimerFromTimer()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assertThat(loop, notNullValue());

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            final long now = System.currentTimeMillis();

            ZLoop.IZLoopHandler timerEvent2 = (loop12, item1, arg1) -> {
                final long now2 = System.currentTimeMillis();
                assertThat(now2 >= now + 10, is(true));
                ((Socket) arg1).send("PING", 0);
                return 0;
            };
            loop13.addTimer(10, 1, timerEvent2, arg);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop1, item, arg) -> {
            received = ((Socket) arg).recvStr(0);
            // Just end the reactor
            return -1;
        };

        // After 10 msecs, fire a timer that registers
        // another timer that sends the ping message
        loop.addTimer(10, 1, timerEvent, input);

        // When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        assertThat(rc, is(0));

        loop.start();

        loop.removePoller(pollInput);
        assertThat(received, is("PING"));

    }

    @Test(timeout = 1000)
    public void testZLoopAddTimerFromSocketHandler()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assertThat(loop, notNullValue());

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop12, item, arg) -> {
            final long now = System.currentTimeMillis();
            ZLoop.IZLoopHandler timerEvent2 = (loop1, item1, arg1) -> {
                final long now2 = System.currentTimeMillis();
                assertThat(now2 >= now + 10, is(true));
                received = ((Socket) arg1).recvStr(0);
                // Just end the reactor
                return -1;
            };
            // After 10 msec fire a timer that ends the reactor
            loop12.addTimer(10, 1, timerEvent2, arg);
            return 0;
        };

        // Fire a timer that sends the ping message
        loop.addTimer(0, 1, timerEvent, input);

        // When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        assertThat(rc, is(0));

        loop.start();

        loop.removePoller(pollInput);
        assertThat(received, is("PING"));
    }

    @Test(timeout = 1000)
    public void testZLoopEndReactorFromTimer()
    {
        int rc;

        ZLoop loop = new ZLoop(ctx);
        assertThat(loop, notNullValue());

        ZLoop.IZLoopHandler timerEvent = (loop13, item, arg) -> {
            ((Socket) arg).send("PING", 0);
            return 0;
        };

        ZLoop.IZLoopHandler socketEvent = (loop12, item, arg) -> {
            // After 10 msecs, fire an event that ends the reactor
            ZLoop.IZLoopHandler shutdownEvent = (loop1, item1, arg1) -> {
                received = ((Socket) arg1).recvStr(0);
                // Just end the reactor
                return -1;
            };
            loop12.addTimer(10, 1, shutdownEvent, arg);
            return 0;
        };

        // Fire event that sends a ping message to output
        loop.addTimer(0, 1, timerEvent, input);

        // When we get the ping message, end the reactor
        PollItem pollInput = new PollItem(output, Poller.POLLIN);
        rc = loop.addPoller(pollInput, socketEvent, output);
        assertThat(rc, is(0));
        loop.start();

        loop.removePoller(pollInput);
        assertThat(received, is("PING"));
    }
}
