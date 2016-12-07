package org.zeromq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class TestZMQ
{
    static class Client extends Thread
    {
        private int port = -1;
        private Socket s = null;

        public Client(Context ctx, int port)
        {
            this.port = port;
            s = ctx.socket(ZMQ.PULL);
        }

        @Override
        public void run()
        {
            System.out.println("Start client thread ");
            s.connect("tcp://127.0.0.1:" + port);
            s.recv(0);

            s.close();
            System.out.println("Stop client thread ");
        }
    }

    @Test
    public void testPollerPollout() throws Exception
    {
        int port = Utils.findOpenPort();

        ZMQ.Context context = ZMQ.context(1);
        Client client = new Client(context, port);

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket(ZMQ.PUSH);
        sender.bind("tcp://127.0.0.1:" + port);

        ZMQ.Poller outItems;
        outItems = context.poller();
        outItems.register(sender, ZMQ.Poller.POLLOUT);

        while (!Thread.currentThread().isInterrupted()) {
            outItems.poll(1000);
            if (outItems.pollout(0)) {
                sender.send("OK", 0);
                System.out.println("ok");
                break;
            }
            else {
                System.out.println("not writable");
                client.start();
            }
        }
        client.join();
        sender.close();
        context.term();
    }

    @Test
    public void testByteBufferSend() throws InterruptedException, IOException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:" + port);
            push.connect("tcp://localhost:" + port);
            bb.put("PING".getBytes(ZMQ.CHARSET));
            bb.flip();
            push.sendByteBuffer(bb, 0);
            String actual = new String(pull.recv(), ZMQ.CHARSET);
            assertEquals("PING", actual);
        }
        finally {
            try {
                push.close();
            }
            catch (Exception ignore) {
            }
            try {
                pull.close();
            }
            catch (Exception ignore) {
            }
            try {
                context.term();
            }
            catch (Exception ignore) {
            }
        }
    }

    @Test
    public void testByteBufferRecv()
    throws InterruptedException, IOException, CharacterCodingException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);
        ByteBuffer bb = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder());
        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:" + port);
            push.connect("tcp://localhost:" + port);
            push.send("PING".getBytes(ZMQ.CHARSET), 0);
            pull.recvByteBuffer(bb, 0);
            bb.flip();
            byte[] b = new byte[bb.remaining()];
            bb.duplicate().get(b);
            assertEquals("PING", new String(b, ZMQ.CHARSET));
        }
        finally {
            try {
                push.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                pull.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                context.term();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }

    }

    @Test
    public void testByteBufferLarge()
    throws InterruptedException, IOException, CharacterCodingException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);
        int[] array = new int[2048 * 2000];
        for (int i = 0; i < array.length; ++i) {
           array[i] = i;
        }
        ByteBuffer bSend = ByteBuffer.allocate(Integer.SIZE / 8 * array.length).order(ByteOrder.nativeOrder());
        bSend.asIntBuffer().put(array);
        ByteBuffer bRec = ByteBuffer.allocate(bSend.capacity()).order(ByteOrder.nativeOrder());
        int[] recArray = new int[array.length];

        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:" + port);
            push.connect("tcp://localhost:" + port);
            push.sendByteBuffer(bSend, 0);
            pull.recvByteBuffer(bRec, 0);
            bRec.flip();
            bRec.asIntBuffer().get(recArray);
            assertArrayEquals(array, recArray);
        }
        finally {
            try {
                push.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                pull.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                context.term();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
    }

    @Test
    public void testByteBufferLargeDirect()
    throws InterruptedException, IOException, CharacterCodingException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);
        int[] array = new int[2048 * 2000];
        for (int i = 0; i < array.length; ++i) {
           array[i] = i;
        }
        ByteBuffer bSend = ByteBuffer.allocateDirect(Integer.SIZE / 8 * array.length).order(ByteOrder.nativeOrder());
        bSend.asIntBuffer().put(array);
        ByteBuffer bRec = ByteBuffer.allocateDirect(bSend.capacity()).order(ByteOrder.nativeOrder());
        int[] recArray = new int[array.length];

        ZMQ.Socket push = null;
        ZMQ.Socket pull = null;
        try {
            push = context.socket(ZMQ.PUSH);
            pull = context.socket(ZMQ.PULL);
            pull.bind("tcp://*:" + port);
            push.connect("tcp://localhost:" + port);
            push.sendByteBuffer(bSend, 0);
            pull.recvByteBuffer(bRec, 0);
            bRec.flip();
            bRec.asIntBuffer().get(recArray);
            assertArrayEquals(array, recArray);
        }
        finally {
            try {
                push.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                pull.close();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
            try {
                context.term();
            }
            catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
    }

    @Test(expected = ZMQException.class)
    public void testBindSameAddress() throws IOException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(ZMQ.REQ);
        ZMQ.Socket socket2 = context.socket(ZMQ.REQ);
        socket1.bind("tcp://*:" + port);
        try {
            socket2.bind("tcp://*:" + port);
            fail("Exception not thrown");
        }
        catch (ZMQException e) {
            assertEquals(e.getErrorCode(), ZMQ.Error.EADDRINUSE.getCode());
            throw e;
        }
        finally {
            socket1.close();
            socket2.close();

            context.term();
        }
    }

    @Test(expected = ZMQException.class)
    public void testBindInprocSameAddress()
    {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(ZMQ.REQ);
        ZMQ.Socket socket2 = context.socket(ZMQ.REQ);
        socket1.bind("inproc://address.already.in.use");
        try {
            socket2.bind("inproc://address.already.in.use");
            fail("Exception not thrown");
        }
        catch (ZMQException e) {
            assertEquals(e.getErrorCode(), ZMQ.Error.EADDRINUSE.getCode());
            throw e;
        }
        finally {
            socket1.close();
            socket2.close();

            context.term();
        }
    }

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
    public void testEventConnectRetried()
    throws InterruptedException, IOException
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

    @Test
    public void testContextBlocky()
    {
       Context ctx = ZMQ.context(1);
       Socket router = ctx.socket(ZMQ.ROUTER);
       long rc = router.getLinger();
       assertEquals(-1, rc);
       router.close();
       ctx.setBlocky(false);
       router = ctx.socket(ZMQ.ROUTER);
       rc = router.getLinger();
       assertEquals(0, rc);
       router.close();
       ctx.term();
    }

    @Test(timeout = 1000)
    public void testSocketDoubleClose()
    {
        Context ctx = ZMQ.context(1);
        Socket socket = ctx.socket(ZMQ.PUSH);
        socket.close();
        socket.close();
        ctx.term();
    }
}
