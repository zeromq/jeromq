package org.zeromq;

import org.junit.Assert;
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
        Assert.assertEquals(0, ctx.getSockets().size());
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
}
