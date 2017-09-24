package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
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
}
