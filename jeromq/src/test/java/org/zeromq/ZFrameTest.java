package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class ZFrameTest
{
    @Test
    public void testZFrameCreation()
    {
        ZFrame f = new ZFrame("Hello".getBytes());
        assertThat(f, notNullValue());
        assertThat(f.hasData(), is(true));
        assertThat(f.size(), is(5));

        f = new ZFrame();
        assertThat(f.hasData(), is(false));
        assertThat(f.size(), is(0));
    }

    @Test
    public void testZFrameEquals()
    {
        ZFrame f = new ZFrame("Hello".getBytes());
        ZFrame clone = f.duplicate();
        assertThat(clone, is(f));
    }

    @Test
    public void testSending()
    {
        ZContext ctx = new ZContext();
        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zframe.test");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zframe.test");

        // Send five different frames, test ZFRAME_MORE
        for (int i = 0; i < 5; i++) {
            ZFrame f = new ZFrame("Hello".getBytes());
            boolean rt = f.send(output, ZMQ.SNDMORE);
            assertThat(rt, is(true));
        }

        // Send same frame five times
        ZFrame f = new ZFrame("Hello".getBytes());
        for (int i = 0; i < 5; i++) {
            f.send(output, ZMQ.SNDMORE);
        }
        assertThat(f.size(), is(5));
        ctx.close();
    }

    @Test
    public void testCopyingAndDuplicating()
    {
        ZFrame f = new ZFrame("Hello");
        ZFrame copy = f.duplicate();
        assertThat(copy, is(f));
        f.destroy();
        assertThat(copy, is(not(f)));
        assertThat(copy.size(), is(5));
    }

    @Test
    public void testReceiving()
    {
        ZContext ctx = new ZContext();
        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zframe.test");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zframe.test");

        // Send same frame five times
        ZFrame f = new ZFrame("Hello".getBytes());
        for (int i = 0; i < 5; i++) {
            f.send(output, ZMQ.SNDMORE);
        }

        // Send END frame
        f = new ZFrame("NOT".getBytes());
        f.reset("END".getBytes());
        assertThat(f.strhex(), is("454E44"));
        f.send(output, 0);

        // Read and count until we receive END
        int frameNbr = 0;
        while (true) {
            f = ZFrame.recvFrame(input);
            frameNbr++;
            if (f.streq("END")) {
                f.destroy();
                break;
            }
        }
        assertThat(frameNbr, is(6));
        f = ZFrame.recvFrame(input, ZMQ.DONTWAIT);
        assertThat(f, nullValue());

        ctx.close();
    }

    @Test
    public void testStringFrames()
    {
        ZContext ctx = new ZContext();
        Socket output = ctx.createSocket(SocketType.PAIR);
        output.bind("inproc://zframe.test");
        Socket input = ctx.createSocket(SocketType.PAIR);
        input.connect("inproc://zframe.test");

        ZFrame f1 = new ZFrame("Hello");
        assertThat(f1.getData().length, is(5));
        f1.send(output, 0);

        ZFrame f2 = ZFrame.recvFrame(input);
        assertThat(f2.hasData(), is(true));
        assertThat(f2.getData().length, is(5));
        assertThat(f2.streq("Hello"), is(true));
        assertThat(f2.toString(), is("Hello"));
        assertThat(f2, is(f1));

        ctx.close();
    }

    @Test
    public void testZMQClientServer()
    {
        try (ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(SocketType.CLIENT);
            Socket server = ctx.createSocket(SocketType.SERVER)) {
            server.bind("inproc://zframe-test.routing");
            client.connect("inproc://zframe-test.routing");

            // Send request from CLIENT to SERVER
            ZFrame request = new ZFrame("Hello");
            boolean rc = request.send(client, 0);
            Assert.assertTrue(rc);

            // Read request and send reply
            request = ZFrame.recvFrame(server);
            int routingId = request.getRoutingId();
            String data = new String(request.getData());
            Assert.assertEquals("Hello", data);
            Assert.assertNotEquals(0, routingId);

            ZFrame reply = new ZFrame("World");
            reply.setRoutingId(routingId);
            rc = reply.send(server, 0);
            Assert.assertTrue(rc);

            // Read reply
            reply = ZFrame.recvFrame(client);
            data = new String(reply.getData());
            Assert.assertEquals("World", data);
            Assert.assertEquals(0, reply.getRoutingId());

            // Client Server Disallow Multipart
            ZFrame frame = new ZFrame("Hello");
            rc = frame.send(client, ZFrame.MORE);
            Assert.assertEquals(false, rc);

            rc = frame.send(server, ZFrame.MORE);
            Assert.assertEquals(false, rc);
        }
    }
}
