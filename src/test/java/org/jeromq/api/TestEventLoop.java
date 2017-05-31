package org.jeromq.api;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertTrue;


public class TestEventLoop {

    @Test(timeout = 1000)
    public void testEventLoop() throws Exception {
        ZeroMQContext context = new ZeroMQContext();

        Socket output = context.createSocket(SocketType.PAIR);
        output.bind("inproc://zloop.test");
        Socket input = context.createSocket(SocketType.PAIR);
        input.connect("inproc://zloop.test");

        EventLoop testClass = new EventLoop();
        testClass.setVerbose(true);

        final AtomicBoolean sawTimerEvent = new AtomicBoolean(false);
        final AtomicBoolean sawSocketEvent = new AtomicBoolean(false);

        EventLoop.EventHandler timerEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) {
                sawTimerEvent.set(true);
                ((Socket) arg).send("PING");
            }
        };

        EventLoop.EventHandler socketEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) {
                sawSocketEvent.set(true);
                throw new EventLoop.EventLoopKiller();
            }
        };

        //  After 10 msecs, send a ping message to output
        testClass.createTimer(10, 1, timerEvent, output);

        //  When we get the ping message, end the reactor
        testClass.addPoller(input, socketEvent, null, PollOption.POLL_IN);
        testClass.start();

        testClass.stopPolling(input);
        context.terminate();

        assertTrue(sawTimerEvent.get());
        assertTrue(sawSocketEvent.get());
    }

    @Test(timeout = 1000)
    public void testZLoopAddTimerFromTimer() {
        ZeroMQContext context = new ZeroMQContext();

        Socket output = context.createSocket(SocketType.PAIR);
        output.bind("inproc://zloop.test");
        Socket input = context.createSocket(SocketType.PAIR);
        input.connect("inproc://zloop.test");

        EventLoop testClass = new EventLoop();
        testClass.setVerbose(true);

        final AtomicBoolean sawTimerEvent = new AtomicBoolean(false);
        final AtomicBoolean sawTimerEvent2 = new AtomicBoolean(false);
        final AtomicBoolean sawSocketEvent = new AtomicBoolean(false);

        EventLoop.EventHandler timerEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                final long now = System.currentTimeMillis();
                sawTimerEvent.set(true);
                EventLoop.EventHandler timerEvent = new EventLoop.EventHandler() {
                    @Override
                    public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                        sawTimerEvent2.set(true);
                        final long now2 = System.currentTimeMillis();
                        assert (now2 >= now + 10);
                        ((Socket) arg).send("PING");
                    }
                };
                loop.createTimer(10, 1, timerEvent, arg);
            }
        };

        EventLoop.EventHandler socketEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                //  Just end the reactor
                sawSocketEvent.set(true);
                throw new EventLoop.EventLoopKiller();
            }
        };

        //  After 10 msecs, fire a timer that registers 
        //  another timer that sends the ping message
        testClass.createTimer(10, 1, timerEvent, output);

        //  When we get the ping message, end the reactor
        testClass.addPoller(input, socketEvent, null, PollOption.POLL_IN);

        testClass.start();

        testClass.stopPolling(input);
        context.terminate();
        assertTrue(sawTimerEvent.get());
        assertTrue(sawTimerEvent2.get());
        assertTrue(sawSocketEvent.get());
    }

    @Test(timeout = 1000)
    public void testZLoopAddTimerFromSocketHandler() {
        ZeroMQContext context = new ZeroMQContext();

        Socket output = context.createSocket(SocketType.PAIR);
        output.bind("inproc://zloop.test");
        Socket input = context.createSocket(SocketType.PAIR);
        input.connect("inproc://zloop.test");

        EventLoop testClass = new EventLoop();
        testClass.setVerbose(true);

        final AtomicBoolean sawTimerEvent = new AtomicBoolean(false);
        final AtomicBoolean sawTimerEvent2 = new AtomicBoolean(false);
        final AtomicBoolean sawSocketEvent = new AtomicBoolean(false);

        EventLoop.EventHandler timerEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                sawTimerEvent.set(true);
                ((Socket) arg).send("PING");
            }
        };

        EventLoop.EventHandler socketEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                sawSocketEvent.set(true);
                final long now = System.currentTimeMillis();
                EventLoop.EventHandler timerEvent2 = new EventLoop.EventHandler() {
                    @Override
                    public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                        final long now2 = System.currentTimeMillis();
                        sawTimerEvent2.set(true);
                        assertTrue(now2 >= now + 10);
                        //  Just end the reactor
                        throw new EventLoop.EventLoopKiller();
                    }
                };
                //  After 10 msec fire a timer that ends the reactor
                loop.createTimer(10, 1, timerEvent2, arg);
            }
        };

        //  Fire a timer that sends the ping message 
        testClass.createTimer(0, 1, timerEvent, output);

        //  When we get the ping message, end the reactor
        testClass.addPoller(input, socketEvent, null, PollOption.POLL_IN);

        testClass.start();

        testClass.stopPolling(input);
        context.terminate();
        assertTrue(sawTimerEvent.get());
        assertTrue(sawTimerEvent2.get());
        assertTrue(sawSocketEvent.get());
    }

    @Test(timeout = 1000)
    public void testZLoopEndReactorFromTimer() {
        ZeroMQContext context = new ZeroMQContext();

        Socket output = context.createSocket(SocketType.PAIR);
        output.bind("inproc://zloop.test");
        Socket input = context.createSocket(SocketType.PAIR);
        input.connect("inproc://zloop.test");

        final AtomicBoolean sawTimerEvent = new AtomicBoolean(false);
        final AtomicBoolean sawTimerEvent2 = new AtomicBoolean(false);
        final AtomicBoolean sawSocketEvent = new AtomicBoolean(false);

        EventLoop testClass = new EventLoop();

        EventLoop.EventHandler timerEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                sawTimerEvent.set(true);
                ((Socket) arg).send("PING");
            }
        };

        EventLoop.EventHandler socketEvent = new EventLoop.EventHandler() {

            @Override
            public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                sawSocketEvent.set(true);
                //  After 10 msecs, fire an event that ends the reactor
                EventLoop.EventHandler shutdownEvent = new EventLoop.EventHandler() {
                    @Override
                    public void handle(EventLoop loop, Socket socket, Object arg) throws EventLoop.EventLoopKiller {
                        sawTimerEvent2.set(true);
                        throw new EventLoop.EventLoopKiller();
                    }
                };
                loop.createTimer(10, 1, shutdownEvent, null);
            }
        };

        //  Fire event that sends a ping message to output
        testClass.createTimer(0, 1, timerEvent, output);

        //  When we get the ping message, end the reactor
        testClass.addPoller(input, socketEvent, null, PollOption.POLL_IN);
        testClass.start();

        testClass.stopPolling(input);
        context.terminate();
        assertTrue(sawTimerEvent.get());
        assertTrue(sawTimerEvent2.get());
        assertTrue(sawSocketEvent.get());
    }

}
