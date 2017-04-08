package org.zeromq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class TestEvents
{
    @Test
    public void testEventConnected()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket helper = context.socket(ZMQ.REQ);
        int port = helper.bindToRandomPort("tcp://127.0.0.1");

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECTED));
        monitor.connect("inproc://monitor.socket");

        socket.connect("tcp://127.0.0.1:" + port);
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECTED, event.getEvent());

        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventConnectDelayed() throws IOException
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_DELAYED));
        monitor.connect("inproc://monitor.socket");

        int randomPort = Utils.findOpenPort();

        socket.connect("tcp://127.0.0.1:" + randomPort);
        event = ZMQ.Event.recv(monitor);
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

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
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
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_LISTENING));
        monitor.connect("inproc://monitor.socket");

        socket.bindToRandomPort("tcp://127.0.0.1");
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

        Socket helper = context.socket(ZMQ.REP);
        int port = helper.bindToRandomPort("tcp://127.0.0.1");

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_BIND_FAILED));
        monitor.connect("inproc://monitor.socket");

        try {
            socket.bind("tcp://127.0.0.1:" + port);
        }
        catch (ZMQException ex) {
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

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        Socket helper = context.socket(ZMQ.REQ);
        monitor.setReceiveTimeOut(100);

        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_ACCEPTED));
        monitor.connect("inproc://monitor.socket");

        int port = socket.bindToRandomPort("tcp://127.0.0.1");

        helper.connect("tcp://127.0.0.1:" + port);
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
        Socket monitor = context.socket(ZMQ.PAIR);
        try {
            ZMQ.Event event;

            Socket socket = context.socket(ZMQ.REP);
            monitor.setReceiveTimeOut(100);

            socket.bindToRandomPort("tcp://127.0.0.1");

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
    public void testEventDisconnected()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        Socket helper = context.socket(ZMQ.REQ);
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

        socket.close();
        monitor.close();
        context.term();
    }

    @Test
    public void testEventMonitorStopped()
    {
        Context context = ZMQ.context(1);
        ZMQ.Event event;

        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
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
