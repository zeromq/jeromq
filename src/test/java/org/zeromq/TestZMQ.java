package org.zeromq;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.Socket.Mechanism;

import zmq.ZError;
import zmq.msg.MsgAllocator;
import zmq.msg.MsgAllocatorDirect;
import zmq.util.Errno;

public class TestZMQ
{
    private Context ctx;

    @Before
    public void setUp()
    {
        ctx = ZMQ.context(1);
        assertThat(ctx, notNullValue());
        new Errno().set(0);
    }

    @After
    public void tearDown()
    {
        ctx.close();
    }

    @Test
    public void testErrno()
    {
        Socket socket = ctx.socket(SocketType.DEALER);
        assertThat(socket.errno(), is(0));

        socket.close();
    }

    @Test(expected = ZMQException.class)
    public void testBindSameAddress() throws IOException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(SocketType.REQ);
        ZMQ.Socket socket2 = context.socket(SocketType.REQ);
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

        ZMQ.Socket socket1 = context.socket(SocketType.REQ);
        ZMQ.Socket socket2 = context.socket(SocketType.REQ);
        socket1.bind("inproc://address.already.in.use");

        socket2.bind("inproc://address.already.in.use");
        assertThat(socket2.errno(), is(ZError.EADDRINUSE));

        socket1.close();
        socket2.close();

        context.term();
    }

    @Test
    public void testSocketUnbind()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        System.out.println("Connecting socket to unbind on port " + port);
        byte[] data = "ABC".getBytes();

        rc = push.send(data);
        assertThat(rc, is(true));
        assertArrayEquals(data, pull.recv());

        rc = pull.unbind("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        rc = push.send(data);
        assertThat(rc, is(true));
        assertNull(pull.recv());

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvArray()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        byte[] data = "ABC".getBytes(ZMQ.CHARSET);

        rc = push.sendMore("DEF");
        assertThat(rc, is(true));
        rc = push.send(data, 0, data.length - 1, 0);
        assertThat(rc, is(true));

        byte[] recvd = pull.recv();
        assertThat(recvd, is("DEF".getBytes(ZMQ.CHARSET)));

        byte[] datb = new byte[2];
        int received = pull.recv(datb, 0, datb.length, 0);
        assertThat(received, is(2));
        assertThat(datb, is("AB".getBytes(ZMQ.CHARSET)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "1248sbfzm";

        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = push.sendPicture(
                              picture,
                              255,
                              65535,
                              429496729,
                              Long.MAX_VALUE,
                              "Hello World",
                              "ABC".getBytes(ZMQ.CHARSET),
                              new ZFrame("My frame"),
                              msg);
        assertThat(rc, is(true));

        Object[] objects = pull.recvPicture(picture);
        assertThat(objects[0], is(equalTo(255)));
        assertThat(objects[1], is(equalTo(65535)));
        assertThat(objects[2], is(equalTo(429496729)));
        assertThat(objects[3], is(equalTo(Long.MAX_VALUE)));
        assertThat(objects[4], is(equalTo("Hello World")));
        assertThat(objects[5], is(equalTo("ABC".getBytes(zmq.ZMQ.CHARSET))));
        assertThat(objects[6], is(equalTo(new ZFrame("My frame"))));
        assertThat(objects[7], is(equalTo(new ZFrame())));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[8], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvBinaryPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "1248sScfm";

        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = push.sendBinaryPicture(
                                    picture,
                                    255,
                                    65535,
                                    429496729,
                                    Long.MAX_VALUE,
                                    "Hello World",
                                    "Hello cruel World!",
                                    "ABC".getBytes(ZMQ.CHARSET),
                                    new ZFrame("My frame"),
                                    msg);
        assertThat(rc, is(true));

        Object[] objects = pull.recvBinaryPicture(picture);
        assertThat(objects[0], is(equalTo(255)));
        assertThat(objects[1], is(equalTo(65535)));
        assertThat(objects[2], is(equalTo(429496729)));
        assertThat(objects[3], is(equalTo(Long.MAX_VALUE)));
        assertThat(objects[4], is(equalTo("Hello World")));
        assertThat(objects[5], is(equalTo("Hello cruel World!")));
        assertThat(objects[6], is(equalTo("ABC".getBytes(zmq.ZMQ.CHARSET))));
        assertThat(objects[7], is(equalTo(new ZFrame("My frame"))));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[8], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testContextBlocky()
    {
        Socket router = ctx.socket(SocketType.ROUTER);
        long rc = router.getLinger();
        assertThat(rc, is(-1L));

        router.close();
        ctx.setBlocky(false);

        router = ctx.socket(SocketType.ROUTER);

        rc = router.getLinger();
        assertThat(rc, is(0L));

        router.close();
    }

    @Test(timeout = 1000)
    public void testSocketDoubleClose()
    {
        Socket socket = ctx.socket(SocketType.PUSH);
        socket.close();
        socket.close();
    }

    @Test
    public void testSubscribe()
    {
        ZMQ.Socket socket = ctx.socket(SocketType.SUB);

        boolean rc = socket.subscribe("abc");
        assertThat(rc, is(true));

        rc = socket.unsubscribe("abc");
        assertThat(rc, is(true));

        rc = socket.unsubscribe("abc".getBytes(ZMQ.CHARSET));
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketAffinity()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());
        socket.setAffinity(42);
        long rc = socket.getAffinity();

        assertThat(rc, is(42L));
        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketBacklog()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setBacklog(42L);
        assertThat(set, is(true));
        int rc = socket.getBacklog();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketConflate()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setConflate(true);
        assertThat(set, is(true));
        boolean rc = socket.getConflate();
        assertThat(rc, is(true));

        set = socket.setConflate(false);
        assertThat(set, is(true));
        rc = socket.isConflate();
        assertThat(rc, is(false));

        socket.close();
    }

    @Test
    public void testSocketConnectRid()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        boolean set = socket.setConnectRid("rid");
        assertThat(set, is(true));

        set = socket.setConnectRid("rid".getBytes(ZMQ.CHARSET));
        assertThat(set, is(true));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketCurveAsServer()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());
        boolean rc = socket.setCurveServer(true);
        assertThat(rc, is(true));

        boolean server = socket.getCurveServer();
        assertThat(server, is(true));

        server = socket.getAsServerCurve();
        assertThat(server, is(true));

        server = socket.isAsServerCurve();
        assertThat(server, is(true));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.CURVE));

        socket.close();
    }

    @Test
    public void testSocketCurveSecret()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x2);

        boolean rc = socket.setCurveSecretKey(key);
        assertThat(rc, is(true));

        byte[] curve = socket.getCurveSecretKey();
        assertThat(curve, is(key));

        boolean server = socket.getCurveServer();
        assertThat(server, is(false));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.CURVE));

        socket.close();
    }

    @Test
    public void testSocketCurvePublic()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.NULL));

        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x2);

        boolean rc = socket.setCurvePublicKey(key);
        assertThat(rc, is(true));

        byte[] curve = socket.getCurvePublicKey();
        assertThat(curve, is(key));

        boolean server = socket.getCurveServer();
        assertThat(server, is(false));

        mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.CURVE));
        socket.close();
    }

    @Test
    public void testSocketCurveServer()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x2);

        boolean rc = socket.setCurveServerKey(key);
        assertThat(rc, is(true));

        byte[] curve = socket.getCurveServerKey();
        assertThat(curve, is(key));

        boolean server = socket.getCurveServer();
        assertThat(server, is(false));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.CURVE));

        socket.close();
    }

    @Test
    public void testSocketHandshake()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setHandshakeIvl(42);
        assertThat(set, is(true));
        int rc = socket.getHandshakeIvl();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketHeartbeatIvl()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setHeartbeatIvl(42);
        assertThat(set, is(true));
        int rc = socket.getHeartbeatIvl();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketHeartbeatTtl()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setHeartbeatTtl(420);
        assertThat(set, is(true));
        int rc = socket.getHeartbeatTtl();
        assertThat(rc, is(400));

        socket.close();
    }

    @Test
    public void testSocketHeartbeatTimeout()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setHeartbeatTimeout(42);
        assertThat(set, is(true));
        int rc = socket.getHeartbeatTimeout();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketHeartbeatContext()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        byte[] context = new byte[3];
        context[0] = 4;
        context[1] = 2;
        context[2] = 1;
        boolean set = socket.setHeartbeatContext(context);
        assertThat(set, is(true));
        byte[] hctx = socket.getHeartbeatContext();
        assertThat(hctx, is(context));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketHWM()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setHWM(42);
        assertThat(set, is(true));
        int rc = socket.getRcvHWM();
        assertThat(rc, is(42));

        rc = socket.getSndHWM();
        assertThat(rc, is(42));

        set = socket.setHWM(43L);
        assertThat(set, is(true));
        rc = socket.getRcvHWM();
        assertThat(rc, is(43));

        rc = socket.getSndHWM();
        assertThat(rc, is(43));

        rc = socket.getHWM();
        assertThat(rc, is(-1));

        socket.close();
    }

    @Test
    public void testSocketIdentity()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        byte[] identity = new byte[42];
        Arrays.fill(identity, (byte) 0x42);
        boolean set = socket.setIdentity(identity);
        assertThat(set, is(true));
        byte[] rc = socket.getIdentity();
        assertThat(rc, is(identity));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketImmediate()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setImmediate(false);
        assertThat(set, is(true));
        boolean rc = socket.getImmediate();
        assertThat(rc, is(false));

        rc = socket.getDelayAttachOnConnect();
        assertThat(rc, is(true));

        set = socket.setImmediate(true);
        assertThat(set, is(true));
        rc = socket.getImmediate();
        assertThat(rc, is(true));

        rc = socket.getDelayAttachOnConnect();
        assertThat(rc, is(false));

        set = socket.setDelayAttachOnConnect(false);
        assertThat(set, is(true));
        rc = socket.getImmediate();
        assertThat(rc, is(true));

        rc = socket.getDelayAttachOnConnect();
        assertThat(rc, is(false));

        set = socket.setDelayAttachOnConnect(true);
        assertThat(set, is(true));
        rc = socket.getImmediate();
        assertThat(rc, is(false));

        rc = socket.getDelayAttachOnConnect();
        assertThat(rc, is(true));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketIPv6()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setIPv6(true);
        assertThat(set, is(true));
        boolean rc = socket.getIPv6();
        assertThat(rc, is(true));
        rc = socket.getIPv4Only();
        assertThat(rc, is(false));

        set = socket.setIPv6(false);
        assertThat(set, is(true));
        rc = socket.getIPv6();
        assertThat(rc, is(false));
        rc = socket.getIPv4Only();
        assertThat(rc, is(true));

        set = socket.setIPv4Only(false);
        assertThat(set, is(true));
        rc = socket.getIPv6();
        assertThat(rc, is(true));
        rc = socket.getIPv4Only();
        assertThat(rc, is(false));

        set = socket.setIPv4Only(true);
        assertThat(set, is(true));
        rc = socket.getIPv6();
        assertThat(rc, is(false));
        rc = socket.getIPv4Only();
        assertThat(rc, is(true));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketLinger()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean set = socket.setLinger(42);
        assertThat(set, is(true));
        int rc = socket.getLinger();
        assertThat(rc, is(42));

        set = socket.setLinger(42L);
        assertThat(set, is(true));
        rc = socket.getLinger();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketMaxMsgSize()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        boolean set = socket.setMaxMsgSize(42);
        assertThat(set, is(true));
        long rc = socket.getMaxMsgSize();
        assertThat(rc, is(42L));

        socket.close();
    }

    @Test
    public void testSocketMsgAllocationThreshold()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        boolean set = socket.setMsgAllocationHeapThreshold(42);
        assertThat(set, is(true));
        int rc = socket.getMsgAllocationHeapThreshold();
        assertThat(rc, is(42));

        socket.close();
    }

    @Test
    public void testSocketMsgAllocator()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        MsgAllocator allocator = new MsgAllocatorDirect();
        boolean set = socket.setMsgAllocator(allocator);
        assertThat(set, is(true));

        // TODO
        socket.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSocketMulticastHops()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        try {
            socket.setMulticastHops(42);
        }
        finally {
            socket.close();
        }
    }

    @Test
    public void testSocketGetMulticastHops()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        long rc = socket.getMulticastHops();
        assertThat(rc, is(1L));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketMulticastLoop()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        try {
            socket.setMulticastLoop(true);
        }
        finally {
            socket.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketHasMulticastLoop()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);
        assertThat(socket, notNullValue());

        boolean rc = socket.hasMulticastLoop();
        assertThat(rc, is(false));
        socket.close();
    }

    @Test
    public void testSocketPlainPassword()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setPlainPassword("password");
        assertThat(rc, is(true));

        String password = socket.getPlainPassword();
        assertThat(password, is("password"));

        boolean server = socket.getPlainServer();
        assertThat(server, is(false));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.PLAIN));

        socket.close();
    }

    @Test
    public void testSocketPlainUsername()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setPlainUsername("username");
        assertThat(rc, is(true));

        String username = socket.getPlainUsername();
        assertThat(username, is("username"));

        boolean server = socket.getPlainServer();
        assertThat(server, is(false));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.PLAIN));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketPlainServer()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setPlainServer(true);
        assertThat(rc, is(true));

        boolean server = socket.getPlainServer();
        assertThat(server, is(true));

        server = socket.isAsServerPlain();
        assertThat(server, is(true));

        server = socket.getAsServerPlain();
        assertThat(server, is(true));

        Mechanism mechanism = socket.getMechanism();
        assertThat(mechanism, is(Mechanism.PLAIN));

        socket.close();
    }

    @Test
    public void testSocketProbeRouter()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setProbeRouter(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSocketRate()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        try {
            socket.setRate(42);
        }
        finally {
            socket.close();
        }
    }

    @Test
    public void testSocketGetRate()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        long rate = socket.getRate();
        assertThat(rate, is(100L));
        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketRcvHwm()
    {
        final Socket socket = ctx.socket(SocketType.DEALER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setRcvHWM(42L);
        assertThat(rc, is(true));

        int hwm = socket.getRcvHWM();
        assertThat(hwm, is(42));

        hwm = socket.getSndHWM();
        assertThat(hwm, is(not(42)));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketSndHwm()
    {
        final Socket socket = ctx.socket(SocketType.DEALER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setSndHWM(42L);
        assertThat(rc, is(true));

        int hwm = socket.getSndHWM();
        assertThat(hwm, is(42));

        hwm = socket.getRcvHWM();
        assertThat(hwm, is(not(42)));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReceiveBufferSize()
    {
        final Socket socket = ctx.socket(SocketType.DEALER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReceiveBufferSize(42L);
        assertThat(rc, is(true));

        int size = socket.getReceiveBufferSize();
        assertThat(size, is(42));

        size = socket.getSendBufferSize();
        assertThat(size, is(not(42)));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketSendBufferSize()
    {
        final Socket socket = ctx.socket(SocketType.DEALER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setSendBufferSize(42L);
        assertThat(rc, is(true));

        int size = socket.getSendBufferSize();
        assertThat(size, is(42));

        size = socket.getReceiveBufferSize();
        assertThat(size, is(not(42)));

        socket.close();
    }

    @Test
    public void testSocketReceiveTimeOut()
    {
        final Socket socket = ctx.socket(SocketType.PAIR);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReceiveTimeOut(42);
        assertThat(rc, is(true));

        int size = socket.getReceiveTimeOut();
        assertThat(size, is(42));

        size = socket.getSendTimeOut();
        assertThat(size, is(not(42)));

        socket.close();
    }

    @Test
    public void testSocketSendTimeOut()
    {
        final Socket socket = ctx.socket(SocketType.PAIR);
        assertThat(socket, notNullValue());

        boolean rc = socket.setSendTimeOut(42);
        assertThat(rc, is(true));

        int size = socket.getSendTimeOut();
        assertThat(size, is(42));

        size = socket.getReceiveTimeOut();
        assertThat(size, is(not(42)));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReconnectIVL()
    {
        final Socket socket = ctx.socket(SocketType.REP);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReconnectIVL(42L);
        assertThat(rc, is(true));

        int reconnect = socket.getReconnectIVL();
        assertThat(reconnect, is(42));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReconnectIVLMax()
    {
        final Socket socket = ctx.socket(SocketType.REP);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReconnectIVLMax(42L);
        assertThat(rc, is(true));

        int reconnect = socket.getReconnectIVLMax();
        assertThat(reconnect, is(42));

        socket.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSocketRecoveryInterval()
    {
        final Socket socket = ctx.socket(SocketType.REP);
        assertThat(socket, notNullValue());

        try {
            socket.setRecoveryInterval(42L);
        }
        finally {
            socket.close();
        }
    }

    @Test
    public void testSocketgetRecoveryInterval()
    {
        final Socket socket = ctx.socket(SocketType.REP);
        assertThat(socket, notNullValue());

        long reconnect = socket.getRecoveryInterval();
        assertThat(reconnect, is(10000L));

        socket.close();
    }

    @Test
    public void testSocketReqCorrelate()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReqCorrelate(true);
        assertThat(rc, is(true));
        rc = socket.setReqCorrelate(false);
        assertThat(rc, is(true));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketGetReqCorrelate()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        try {
            socket.getReqCorrelate();
        }
        finally {
            socket.close();
        }
    }

    @Test
    public void testSocketReqRelaxed()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setReqRelaxed(true);
        assertThat(rc, is(true));
        rc = socket.setReqRelaxed(false);
        assertThat(rc, is(true));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketGetReqRelaxed()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        try {
            socket.getReqRelaxed();
        }
        finally {
            socket.close();
        }
    }

    @Test
    public void testSocketRouterHandover()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setRouterHandover(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketRouterMandatory()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setRouterMandatory(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketRouterRaw()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);
        assertThat(socket, notNullValue());

        boolean rc = socket.setRouterRaw(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketSocksProxy()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setSocksProxy("abc");
        assertThat(rc, is(true));

        String proxy = socket.getSocksProxy();
        assertThat(proxy, is("abc"));

        rc = socket.setSocksProxy("def".getBytes(ZMQ.CHARSET));
        assertThat(rc, is(true));

        proxy = socket.getSocksProxy();
        assertThat(proxy, is("def"));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketSwap()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        try {
            socket.setSwap(42L);
        }
        finally {
            socket.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketGetSwap()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        long rc = socket.getSwap();
        assertThat(rc, is(-1L));

        socket.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketTCPKeepAlive()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setTCPKeepAlive(1L);
        assertThat(rc, is(true));

        int tcp = socket.getTCPKeepAlive();
        assertThat(tcp, is(1));

        long tcpl = socket.getTCPKeepAliveSetting();
        assertThat(tcpl, is(1L));

        socket.close();
    }

    @Test
    public void testSocketTCPKeepAliveCount()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setTCPKeepAliveCount(42);
        assertThat(rc, is(true));

        long tcp = socket.getTCPKeepAliveCount();
        assertThat(tcp, is(42L));

        socket.close();
    }

    @Test
    public void testSocketTCPKeepAliveInterval()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setTCPKeepAliveInterval(42);
        assertThat(rc, is(true));

        long tcp = socket.getTCPKeepAliveInterval();
        assertThat(tcp, is(42L));

        socket.close();
    }

    @Test
    public void testSocketTCPKeepAliveIdle()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setTCPKeepAliveIdle(42);
        assertThat(rc, is(true));

        long tcp = socket.getTCPKeepAliveIdle();
        assertThat(tcp, is(42L));

        socket.close();
    }

    @Test
    public void testSocketTos()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        boolean rc = socket.setTos(42);
        assertThat(rc, is(true));

        int tos = socket.getTos();
        assertThat(tos, is(42));

        socket.close();
    }

    @Test
    public void testSocketType()
    {
        final Socket socket = ctx.socket(SocketType.REQ);
        assertThat(socket, notNullValue());

        SocketType rc = socket.getSocketType();
        assertThat(rc, is(SocketType.REQ));

        socket.close();
    }

    @Test
    public void testSocketXpubNoDrop()
    {
        final Socket socket = ctx.socket(SocketType.XPUB);
        assertThat(socket, notNullValue());

        boolean rc = socket.setXpubNoDrop(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketXpubVerbose()
    {
        final Socket socket = ctx.socket(SocketType.XPUB);
        assertThat(socket, notNullValue());

        boolean rc = socket.setXpubVerbose(true);
        assertThat(rc, is(true));

        socket.close();
    }

    @Test
    public void testSocketZAPDomain()
    {
        final Socket socket = ctx.socket(SocketType.XPUB);
        assertThat(socket, notNullValue());

        boolean rc = socket.setZAPDomain("abc");
        assertThat(rc, is(true));

        String domain = socket.getZAPDomain();
        assertThat(domain, is("abc"));

        domain = socket.getZapDomain();
        assertThat(domain, is("abc"));

        rc = socket.setZapDomain("def");
        assertThat(rc, is(true));

        domain = socket.getZapDomain();
        assertThat(domain, is("def"));

        domain = socket.getZAPDomain();
        assertThat(domain, is("def"));

        rc = socket.setZapDomain("ghi".getBytes(ZMQ.CHARSET));
        assertThat(rc, is(true));

        domain = socket.getZapDomain();
        assertThat(domain, is("ghi"));

        domain = socket.getZAPDomain();
        assertThat(domain, is("ghi"));

        rc = socket.setZAPDomain("jkl".getBytes(ZMQ.CHARSET));
        assertThat(rc, is(true));

        domain = socket.getZapDomain();
        assertThat(domain, is("jkl"));

        domain = socket.getZAPDomain();
        assertThat(domain, is("jkl"));

        socket.close();
    }

    @Test
    public void testSocketLocalAddressPropertyName()
    {
        try (final Socket socket = ctx.socket(SocketType.XPUB)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSelfAddressPropertyName("X-LocalAddress");
            assertThat(rc, is(true));

            String propertyName = socket.getSelfAddressPropertyName();
            assertThat(propertyName, is("X-LocalAddress"));
        }
    }
}
