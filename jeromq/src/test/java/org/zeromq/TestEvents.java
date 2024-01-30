package org.zeromq;

import java.io.IOException;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestEvents
{
    @Test
    public void testEventConnectedContext()
    {
        try (Context context = new Context(1);
             Socket helper = context.socket(SocketType.REQ);
             Socket socket = context.socket(SocketType.REP);
             Socket monitor = context.socket(SocketType.PAIR)
        ) {
            int port = helper.bindToRandomPort("tcp://127.0.0.1");

            monitor.setReceiveTimeOut(100);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECTED));
            monitor.connect("inproc://monitor.socket");

            socket.connect("tcp://127.0.0.1:" + port);
            ZMQ.Event event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_CONNECTED, event.getEvent());
        }
    }

    @Test
    public void testEventConnected()
    {
        try (ZContext context = new ZContext(1);
            Socket helper = context.createSocket(SocketType.REQ);
            Socket socket = context.createSocket(SocketType.REP);
            Socket monitor = context.createSocket(SocketType.PAIR)
        ) {
            int port = helper.bindToRandomPort("tcp://127.0.0.1");

            monitor.setReceiveTimeOut(100);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECTED));
            monitor.connect("inproc://monitor.socket");

            socket.connect("tcp://127.0.0.1:" + port);
            ZMQ.Event event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_CONNECTED, event.getEvent());
        }
    }

    @Test
    public void testEventConnectDelayed() throws IOException
    {
        Context context = ZMQ.context(1);

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_DELAYED));
        monitor.connect("inproc://monitor.socket");

        int randomPort = Utils.findOpenPort();

        socket.connect("tcp://127.0.0.1:" + randomPort);
        ZMQ.Event event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_DELAYED, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventConnectRetried() throws InterruptedException, IOException
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_RETRIED));
        monitor.connect("inproc://monitor.socket");

        int randomPort = Utils.findOpenPort();

        socket.connect("tcp://127.0.0.1:" + randomPort);
        Thread.sleep(1000L); // on windows, this is required, otherwise test fails
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_RETRIED, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventListening()
    {
        try (ZContext context = new ZContext(1);
                Socket socket = context.createSocket(SocketType.REP);
                Socket monitor = context.createSocket(SocketType.PAIR)
        ) {
            monitor.setReceiveTimeOut(100);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_LISTENING));
            monitor.connect("inproc://monitor.socket");

            socket.bindToRandomPort("tcp://127.0.0.1");
            ZMQ.Event event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_LISTENING, event.getEvent());
        }
    }

    @Test
    public void testEventBindFailed()
    {
        try (ZContext context = new ZContext(1);
                Socket helper = context.createSocket(SocketType.REP);
                Socket socket = context.createSocket(SocketType.REP);
                Socket monitor = context.createSocket(SocketType.PAIR)
        ) {
            ZMQ.Event event;

            int port = helper.bindToRandomPort("tcp://127.0.0.1");
            monitor.setReceiveTimeOut(100);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_BIND_FAILED));
            monitor.connect("inproc://monitor.socket");

            socket.bind("tcp://127.0.0.1:" + port);
            event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_BIND_FAILED, event.getEvent());
        }
    }

    @Test
    public void testEventAccepted()
    {
        try (ZContext context = new ZContext(1);
                Socket helper = context.createSocket(SocketType.REP);
                Socket socket = context.createSocket(SocketType.REP);
                Socket monitor = context.createSocket(SocketType.PAIR)
        ) {
            ZMQ.Event event;
            monitor.setReceiveTimeOut(100);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_ACCEPTED));
            monitor.connect("inproc://monitor.socket");

            int port = socket.bindToRandomPort("tcp://127.0.0.1");

            helper.connect("tcp://127.0.0.1:" + port);
            event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_ACCEPTED, event.getEvent());
        }
    }

    @Test
    public void testEventClosed()
    {
        try (ZContext context = new ZContext(1);
             Socket monitor = context.createSocket(SocketType.PAIR)
        ) {
            ZMQ.Event event;

            Socket socket = context.createSocket(SocketType.REP);
            monitor.setReceiveTimeOut(100);

            socket.bindToRandomPort("tcp://127.0.0.1");

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CLOSED));
            monitor.connect("inproc://monitor.socket");

            socket.close();
            event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_CLOSED, event.getEvent());

        }
    }

    @Test
    public void testEventDisconnected()
    {
        try (ZContext context = new ZContext(1);
             Socket socket = context.createSocket(SocketType.REP);
             Socket monitor = context.createSocket(SocketType.PAIR);
             Socket helper = context.createSocket(SocketType.REQ)
        ) {
            ZMQ.Event event;
            monitor.setReceiveTimeOut(100);

            int port = socket.bindToRandomPort("tcp://127.0.0.1");
            helper.connect("tcp://127.0.0.1:" + port);

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_DISCONNECTED));
            monitor.connect("inproc://monitor.socket");

            zmq.ZMQ.sleep(1);

            helper.close();
            event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_DISCONNECTED, event.getEvent());
        }
    }

    @Test
    public void testEventMonitorStopped()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_MONITOR_STOPPED));
        monitor.connect("inproc://monitor.socket");

        socket.monitor(null, 0);
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_MONITOR_STOPPED, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
    }
}
