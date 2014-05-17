package org.zeromq;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZContext {

    @Test
    public void testZContext() {
        ZContext ctx = new ZContext();
        Socket s1 = ctx.createSocket(ZMQ.PAIR);
        Socket s2 = ctx.createSocket(ZMQ.XREQ);
        Socket s3 = ctx.createSocket(ZMQ.REQ);
        Socket s4 = ctx.createSocket(ZMQ.REP);
        Socket s5 = ctx.createSocket(ZMQ.PUB);
        Socket s6 = ctx.createSocket(ZMQ.SUB);
        ctx.close();
        Assert.assertEquals(0, ctx.getSockets().size());
    }
}
