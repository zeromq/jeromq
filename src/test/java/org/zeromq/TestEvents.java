package org.zeromq;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import zmq.util.AndroidIgnore;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestEvents
{
    @Test
    public void testEventConnected()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket helper = context.socket(SocketType.REQ);
        boolean rc = helper.bind("tcp://*:*");
        assertThat(rc, is(true));

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECTED));
        monitor.connect("inproc://monitor.socket");

        socket.connect(helper.getLastEndpoint());
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECTED, event.getEvent());

        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventConnectDelayed()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_DELAYED));
        monitor.connect("inproc://monitor.socket");

        socket.connect("tcp://*:*");
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_DELAYED, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventConnectRetried() throws InterruptedException
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_RETRIED));
        monitor.connect("inproc://monitor.socket");

        socket.connect("tcp://*:*");
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
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_LISTENING));
        monitor.connect("inproc://monitor.socket");

        socket.bind("tcp://*:*");
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_LISTENING, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventBindFailed()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket helper = context.socket(SocketType.REP);
        boolean rc = helper.bind("tcp://*:*");
        assertThat(rc, is(true));
        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_BIND_FAILED));
        monitor.connect("inproc://monitor.socket");

        try {
            socket.bind(helper.getLastEndpoint());
        }
        catch (ZMQException ignored) {
        }
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_BIND_FAILED, event.getEvent());

        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventAccepted()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        Socket helper = context.socket(SocketType.REQ);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_ACCEPTED));
        monitor.connect("inproc://monitor.socket");

        boolean rc = socket.bind("tcp://*:*");
        assertThat(rc, is(true));

        helper.connect(socket.getLastEndpoint());
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_ACCEPTED, event.getEvent());

        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventClosed()
    {
        Context context = ZMQ.context(1);
        Socket monitor = context.socket(SocketType.PAIR);
        try {
            ZMQ.Event event;

            Socket socket = context.socket(SocketType.REP);
            monitor.setReceiveTimeOut(100);

            socket.bind("tcp://*:*");

            assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CLOSED));
            monitor.connect("inproc://monitor.socket");

            socket.close();
            event = ZMQ.Event.recv(monitor);
            assertNotNull("No event was received", event);
            assertEquals(ZMQ.EVENT_CLOSED, event.getEvent());
        }
        finally {
            monitor.close();
            context.term();
        }
    }

    @Test
    @AndroidIgnore
    public void testEventDisconnected()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(SocketType.REP);
        Socket monitor = context.socket(SocketType.PAIR);
        Socket helper = context.socket(SocketType.REQ);
        monitor.setReceiveTimeOut(100);

        boolean rc = socket.bind("tcp://*:*");
        assertThat(rc, is(true));
        helper.connect(socket.getLastEndpoint());

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_DISCONNECTED));
        monitor.connect("inproc://monitor.socket");

        zmq.ZMQ.sleep(1);

        helper.close();
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_DISCONNECTED, event.getEvent());

        socket.close();
        monitor.close();
        context.term();
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
