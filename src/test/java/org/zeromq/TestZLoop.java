/*
    Copyright other contributors as noted in the AUTHORS file.

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.zeromq;

import org.junit.After;
import org.junit.Assert;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.PollItem;
import org.junit.Test;
import org.junit.Before;

public class TestZLoop {

    private String received;
    private ZContext ctx;
    private Socket input;
    private Socket output;

    @Before
    public void setUp()
    {
        ctx = new ZContext();
        assert (ctx != null);

        output = ctx.createSocket(ZMQ.PAIR);
        assert (output != null);
        output.bind("inproc://zloop.test");
        input = ctx.createSocket(ZMQ.PAIR);
        assert (input != null);
        input.connect( "inproc://zloop.test");

        received = "FAILED";
    }

    @After
    public void tearDown()
    {
        ctx.destroy();
    }

    @Test
    public void testZLoop() {
        int rc = 0;

        ZLoop loop = new ZLoop();
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                ((Socket) arg).send("PING", 0);
                return 0;
            }
        };

        ZLoop.IZLoopHandler socketEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                received = ((Socket) arg).recvStr(0);
                //  Just end the reactor
                return -1;
            }
        };

        //  After 10 msecs, send a ping message to output
        loop.addTimer(10, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem( output, Poller.POLLIN );
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);
        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }

    @Test
    public void testZLoopAddTimerFromTimer() {
        int rc = 0;

        ZLoop loop = new ZLoop();
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                final long now = System.currentTimeMillis();

                ZLoop.IZLoopHandler timerEvent2 = new ZLoop.IZLoopHandler() {
                    @Override
                    public int handle(ZLoop loop, PollItem item, Object arg) {
                        final long now2 = System.currentTimeMillis();
                        assert (now2 >= now + 10);
                        ((Socket) arg).send("PING", 0);
                        return 0;
                    }
                };
                loop.addTimer(10, 1, timerEvent2, arg);
                return 0;
            }
        };

        ZLoop.IZLoopHandler socketEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                received = ((Socket) arg).recvStr(0);
                //  Just end the reactor
                return -1;
            }
        };

        //  After 10 msecs, fire a timer that registers
        //  another timer that sends the ping message
        loop.addTimer(10, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem( output, Poller.POLLIN );
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);

        loop.start();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);

    }

    @Test(timeout = 1000)
    public void testZLoopAddTimerFromSocketHandler() {
        int rc = 0;

        ZLoop loop = new ZLoop();
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                ((Socket) arg).send("PING", 0);
                return 0;
            }
        };

        ZLoop.IZLoopHandler socketEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                final long now = System.currentTimeMillis();
                ZLoop.IZLoopHandler timerEvent2 = new ZLoop.IZLoopHandler() {
                    @Override
                    public int handle(ZLoop loop, PollItem item, Object arg) {
                        final long now2 = System.currentTimeMillis();
                        Assert.assertTrue(now2 >= now + 10);
                        received = ((Socket) arg).recvStr(0);
                        //  Just end the reactor
                        return -1;
                    }
                };
                //  After 10 msec fire a timer that ends the reactor
                loop.addTimer(10, 1, timerEvent2, arg);
                return 0;
            }
        };

        //  Fire a timer that sends the ping message
        loop.addTimer(0, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem( output, Poller.POLLIN );
        rc = loop.addPoller(pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);

        loop.start ();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }

    @Test(timeout = 1000)
    public void testZLoopEndReactorFromTimer() {
        int rc = 0;

        ZLoop loop = new ZLoop();
        assert (loop != null);

        ZLoop.IZLoopHandler timerEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                ((Socket)arg).send("PING", 0);
                return 0;
            }
        };

        ZLoop.IZLoopHandler socketEvent = new ZLoop.IZLoopHandler() {

            @Override
            public int handle(ZLoop loop, PollItem item, Object arg) {
                //  After 10 msecs, fire an event that ends the reactor
                ZLoop.IZLoopHandler shutdownEvent = new ZLoop.IZLoopHandler() {
                    @Override
                    public int handle(ZLoop loop, PollItem item, Object arg) {
                        received = ((Socket) arg).recvStr(0);
                        //  Just end the reactor
                        return -1;
                    }
                };
                loop.addTimer (10, 1, shutdownEvent, arg);
                return 0;
            }
        };

        //  Fire event that sends a ping message to output
        loop.addTimer (0, 1, timerEvent, input);

        //  When we get the ping message, end the reactor
        PollItem pollInput = new PollItem( output, Poller.POLLIN );
        rc = loop.addPoller (pollInput, socketEvent, output);
        Assert.assertEquals(0, rc);
        loop.start ();

        loop.removePoller(pollInput);
        Assert.assertEquals("PING", received);
    }
}
