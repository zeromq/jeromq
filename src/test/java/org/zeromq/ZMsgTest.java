package org.zeromq;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by hartmann on 3/21/14.
 */
public class ZMsgTest {

    @Test
    public void testRecvFrame() throws Exception {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket  socket   = ctx.socket(ZMQ.PULL);

        ZFrame f = ZFrame.recvFrame(socket, ZMQ.NOBLOCK);
        Assert.assertNull(f);
    }

    @Test
    public void testRecvMsg() throws Exception {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket  socket   = ctx.socket(ZMQ.PULL);

        ZMsg msg = ZMsg.recvMsg(socket, ZMQ.NOBLOCK);
        Assert.assertNull(msg);
    }

    @Test
    public void testRecvNullByteMsg() throws Exception {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket sender = ctx.socket(ZMQ.PUSH);
        ZMQ.Socket receiver = ctx.socket(ZMQ.PULL);

        receiver.bind("inproc://" + this.hashCode());
        sender.connect("inproc://" +this.hashCode());

        sender.send(new byte[0]);
        ZMsg msg = ZMsg.recvMsg(receiver, ZMQ.NOBLOCK);
        Assert.assertNotNull(msg);
    }



}
