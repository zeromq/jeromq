package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZContext
{
    @Test(timeout = 5000)
    public void testZContext()
    {
        ZContext ctx = new ZContext();
        ctx.createSocket(SocketType.PAIR);
        ctx.createSocket(SocketType.REQ);
        ctx.createSocket(SocketType.REP);
        ctx.createSocket(SocketType.PUB);
        ctx.createSocket(SocketType.SUB);
        ctx.close();
        assertThat(ctx.getSockets().isEmpty(), is(true));
    }

    @Test(timeout = 5000)
    public void testZContextSocketCloseBeforeContextClose()
    {
        ZContext ctx = new ZContext();
        Socket s1 = ctx.createSocket(SocketType.PUSH);
        Socket s2 = ctx.createSocket(SocketType.PULL);
        s1.close();
        s2.close();
        ctx.close();
    }

    @Test(timeout = 5000)
    public void testZContextLinger()
    {
        try (ZContext ctx = new ZContext()) {
            ctx.setLinger(125);
            Socket s = ctx.createSocket(SocketType.PUSH);
            assertThat(ctx.getLinger(), is(125));
            assertThat(s.getLinger(), is(125));
        }
    }

    @Test(timeout = 5000)
    public void testConstruction()
    {
        ZContext ctx = new ZContext();
        assertThat(ctx, notNullValue());
        assertThat(ctx.getContext(), notNullValue());
        assertThat(ctx.isClosed(), is(false));
        assertThat(ctx.getIoThreads(), is(1));
        assertThat(ctx.getLinger(), is(0));
        assertThat(ctx.isMain(), is(true));
    }

    @SuppressWarnings("deprecation")
    @Test(timeout = 5000)
    public void testDestruction()
    {
        ZContext ctx = new ZContext();
        ctx.close();
        assertThat(ctx.isClosed(), is(true));
        assertThat(ctx.getSockets().isEmpty(), is(true));

        // Ensure context is not destroyed if not in main thread
        ZContext ctx1 = new ZContext();
        ctx1.setMain(false);
        @SuppressWarnings("unused")
        Socket s = ctx1.createSocket(SocketType.PAIR);
        ctx1.close();
        assertThat(ctx1.getSockets().isEmpty(), is(true));
        assertThat(ctx1.getContext(), notNullValue());
    }

    @Test(timeout = 5000)
    public void testAddingSockets() throws ZMQException
    {
        try (ZContext ctx = new ZContext()) {
            Socket s = ctx.createSocket(SocketType.PUB);
            assertThat(s, notNullValue());
            assertThat(s.getSocketType(), is(SocketType.PUB));
            Socket s1 = ctx.createSocket(SocketType.REQ);
            assertThat(s1, notNullValue());
            assertThat(s1.getSocketType(), is(SocketType.REQ));
            assertThat(ctx.getSockets().size(), is(2));
        }
    }

    @Test(timeout = 5000)
    public void testRemovingSockets() throws ZMQException
    {
        ZContext ctx = new ZContext();
        try (Socket s = ctx.createSocket(SocketType.PUB)) {
            assertThat(s, notNullValue());
            assertThat(ctx.getSockets().size(), is(1));
        }
        finally {
            assertThat(ctx.getSockets().size(), is(0));
            ctx.close();
        }
    }

    @Test(timeout = 5000)
    public void testShadow()
    {
        ZContext ctx = new ZContext();
        // A socket that will be forgotten at the parent
        Socket s = ctx.createSocket(SocketType.SUB);
        Assert.assertNotNull(s);
        Assert.assertEquals(1, ctx.getSockets().size());
        // A shadow that will be forgotten
        ZContext shadowCtx0 = ctx.shadow();
        // A socket that will be forgotten in a shadow
        @SuppressWarnings("unused")
        Socket s0 = shadowCtx0.createSocket(SocketType.PUB);
        try (ZContext shadowCtx1 = ctx.shadow()) {
            Assert.assertEquals(0, shadowCtx1.getSockets().size());
            try (Socket s1 = shadowCtx1.createSocket(SocketType.PUB)) {
                Assert.assertEquals(1, ctx.getSockets().size());
                Assert.assertEquals(1, shadowCtx1.getSockets().size());
            }
        }
        ctx.close();
        Assert.assertTrue(ctx.isEmpty());
    }

    @Test(timeout = 5000)
    public void testSeveralPendingInprocSocketsAreClosedIssue595()
    {
        ZContext ctx = new ZContext();

        for (SocketType type : SocketType.values()) {
            for (int idx = 0; idx < 3; ++idx) {
                Socket socket = ctx.createSocket(type);
                assertThat(socket, notNullValue());
                boolean rc = socket.connect("inproc://" + type.name());
                assertThat(rc, is(true));
            }
        }
        ctx.close();

        assertThat(ctx.isClosed(), is(true));
    }

    @Test(timeout = 1000)
    public void testInterruptedClose() throws Throwable
    {
        ZContext ctx = new ZContext();
        Socket s = ctx.createSocket(SocketType.PULL);
        int port = Utils.findOpenPort();
        s.bind("tcp://*:" + port);
        Thread.currentThread().interrupt();
        s.close();
        ZMQException ex = Assert.assertThrows(ZMQException.class, ctx::close);
        assertThat(ex.getErrorCode(), is(4));
        assertThat(Thread.interrupted(), is(false));
    }
}
