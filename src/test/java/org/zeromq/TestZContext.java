package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZContext
{
    @SuppressWarnings("deprecation")
    @Test
    public void testZContext()
    {
        ZContext ctx = new ZContext();
        ctx.createSocket(ZMQ.PAIR);
        ctx.createSocket(ZMQ.XREQ);
        ctx.createSocket(ZMQ.REQ);
        ctx.createSocket(ZMQ.REP);
        ctx.createSocket(ZMQ.PUB);
        ctx.createSocket(ZMQ.SUB);
        ctx.close();
        assertThat(ctx.getSockets().isEmpty(), is(true));
    }

    @Test
    public void testZContextSocketCloseBeforeContextClose()
    {
        ZContext ctx = new ZContext();
        Socket s1 = ctx.createSocket(ZMQ.PUSH);
        Socket s2 = ctx.createSocket(ZMQ.PULL);
        s1.close();
        s2.close();
        ctx.close();
    }

    @Test
    public void testZContextLinger()
    {
        ZContext ctx = new ZContext();
        int linger = ctx.getLinger();
        assertThat(linger, is(0));

        final int newLinger = 1000;
        ctx.setLinger(newLinger);
        linger = ctx.getLinger();
        assertThat(linger, is(newLinger));
        ctx.close();
    }

    @Test
    public void testConstruction()
    {
        ZContext ctx = new ZContext();
        assertThat(ctx, notNullValue());
        assertThat(ctx.getIoThreads(), is(1));
        assertThat(ctx.getLinger(), is(0));
        assertThat(ctx.isMain(), is(true));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDestruction()
    {
        ZContext ctx = new ZContext();
        ctx.close();
        assertThat(ctx.getSockets().isEmpty(), is(true));

        // Ensure context is not destroyed if not in main thread
        ZContext ctx1 = new ZContext();
        ctx1.setMain(false);
        @SuppressWarnings("unused")
        Socket s = ctx1.createSocket(ZMQ.PAIR);
        ctx1.close();
        assertThat(ctx1.getSockets().isEmpty(), is(true));
        assertThat(ctx1.getContext(), notNullValue());
    }

    @Test
    public void testAddingSockets() throws ZMQException
    {
        ZContext ctx = new ZContext();
        try {
            Socket s = ctx.createSocket(ZMQ.PUB);
            assertThat(s, notNullValue());
            assertThat(s.getType(), is(ZMQ.PUB));
            Socket s1 = ctx.createSocket(ZMQ.REQ);
            assertThat(s1, notNullValue());
            assertThat(s1.getType(), is(ZMQ.REQ));
            assertThat(ctx.getSockets().size(), is(2));
        }
        finally {
            ctx.close();
        }
    }

    @Test
    public void testRemovingSockets() throws ZMQException
    {
        ZContext ctx = new ZContext();
        try {
            Socket s = ctx.createSocket(ZMQ.PUB);
            assertThat(s, notNullValue());
            assertThat(ctx.getSockets().size(), is(1));

            ctx.destroySocket(s);
            assertThat(ctx.getSockets().size(), is(0));
        }
        finally {
            ctx.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testShadow()
    {
        ZContext ctx = new ZContext();
        Socket s = ctx.createSocket(ZMQ.PUB);
        assertThat(s, notNullValue());
        assertThat(ctx.getSockets().size(), is(1));

        ZContext shadowCtx = ZContext.shadow(ctx);
        shadowCtx.setMain(false);
        assertThat(shadowCtx.getSockets().size(), is(0));
        @SuppressWarnings("unused")
        Socket s1 = shadowCtx.createSocket(ZMQ.SUB);
        assertThat(shadowCtx.getSockets().size(), is(1));
        assertThat(ctx.getSockets().size(), is(1));

        shadowCtx.close();
        ctx.close();
    }
}
