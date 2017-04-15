package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import zmq.ZError;

public class TestZMQ
{
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

        Socket push = context.socket(ZMQ.PUSH);
        Socket pull = context.socket(ZMQ.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
//        rc = push.setImmediate(false);
//        assertThat(rc, is(true));
//        rc = pull.setImmediate(false);
//        assertThat(rc, is(true));
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
