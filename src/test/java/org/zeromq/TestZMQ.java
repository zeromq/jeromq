package org.zeromq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestZMQ
{
    static class Client extends Thread {

        private Socket s = null;
        public Client (Context ctx) {
            s = ctx.socket(ZMQ.PULL);
        }

        @Override
        public void run () {
            System.out.println("Start client thread ");
            s.connect( "tcp://127.0.0.1:6669");
            s.recv(0);

            s.close();
            System.out.println("Stop client thread ");
        }
    }

    @Test
    public void testPollerPollout () throws Exception
    {
        ZMQ.Context context = ZMQ.context(1);
        Client client = new Client (context);

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket (ZMQ.PUSH);
        sender.bind ("tcp://127.0.0.1:6669");

        ZMQ.Poller outItems;
        outItems = context.poller ();
        outItems.register (sender, ZMQ.Poller.POLLOUT);


        while (!Thread.currentThread ().isInterrupted ()) {

            outItems.poll (1000);
            if (outItems.pollout (0)) {
                sender.send ("OK", 0);
                System.out.println ("ok");
                break;
            } else {
                System.out.println ("not writable");
                client.start ();
            }
        }
        client.join ();
        sender.close ();
        context.term ();
    }
    
    @Test
    public void testByteBufferSend() throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:12344");
            push.connect("tcp://localhost:12344");
            bb.put("PING".getBytes(ZMQ.CHARSET));
            bb.flip();
            push.sendByteBuffer(bb, 0);
            String actual = new String(pull.recv(), ZMQ.CHARSET);
            assertEquals("PING", actual);
        } finally {
            try {
                push.close();
            } catch (Exception ignore) {
            }
            try {
                pull.close();
            } catch (Exception ignore) {
            }
            try {
                context.term();
            } catch (Exception ignore) {
            }
        }

    }

    @Test
    public void testByteBufferRecv() throws InterruptedException, CharacterCodingException {
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:12345");
            push.connect("tcp://localhost:12345");
            push.send("PING".getBytes(ZMQ.CHARSET), 0);
            pull.recvByteBuffer(bb, 0);
            bb.flip();
            byte[] b = new byte[bb.remaining()];
            bb.duplicate().get(b);
            assertEquals("PING", new String(b, ZMQ.CHARSET));
        } finally {
            try {
                push.close();
            } catch (Exception ignore) {
            }
            try {
                pull.close();
            } catch (Exception ignore) {
            }
            try {
                context.term();
            } catch (Exception ignore) {
            }
        }

    }

    @Test(expected = ZMQException.class)
    public void testBindSameAddress()
    {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(ZMQ.REQ);
        ZMQ.Socket socket2 = context.socket(ZMQ.REQ);
        socket1.bind("tcp://*:12346");
        try
        {
            socket2.bind("tcp://*:12346");
            fail("Exception not thrown");
        } catch (ZMQException e)
        {
            assertEquals(e.getErrorCode(), ZMQ.Error.EADDRINUSE.getCode());
            throw e;
        } finally {
            socket1.close();
            socket2.close();

            context.term();
        }
    }
    
    @Test
    public void testEventConnected() {
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
    public void testEventConnectDelayed() {
        Context context = ZMQ.context(1);
        ZMQ.Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_DELAYED));
        monitor.connect("inproc://monitor.socket");
        
        socket.connect("tcp://127.0.0.1:6751");
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_DELAYED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventConnectRetried() {
        Context context = ZMQ.context(1);
        ZMQ.Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_RETRIED));
        monitor.connect("inproc://monitor.socket");
        
        socket.connect("tcp://127.0.0.1:6752");
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_RETRIED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventListening() {
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
    public void testEventBindFailed() {
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
        } catch (ZMQException ex) {}
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_BIND_FAILED, event.getEvent());
        
        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventAccepted() {
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
    public void testEventClosed() {
        Context context = ZMQ.context(1);
        ZMQ.Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        socket.bindToRandomPort("tcp://127.0.0.1");
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CLOSED));
        monitor.connect("inproc://monitor.socket");
        
        socket.close();
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CLOSED, event.getEvent());
        
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventDisconnected() {
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
        
        helper.close();
        event = ZMQ.Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_DISCONNECTED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventMonitorStopped() {
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

    @Test
    public void testSocketUnbind()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(ZMQ.PUSH);
        Socket pull = context.socket(ZMQ.PULL);
        pull.setReceiveTimeOut(50);

        int port = pull.bindToRandomPort("tcp://127.0.0.1");
        push.connect("tcp://127.0.0.1:" + port);

        byte[] data = "ABC".getBytes();

        push.send(data);
        assertArrayEquals(data, pull.recv());

        pull.unbind("tcp://127.0.0.1:" + port);

        push.send(data);
        assertNull(pull.recv());

        push.close();
        pull.close();
        context.term();
    }
}
