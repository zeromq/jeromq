package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Assert;
import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class SubTest
{
    @Test
    public void testHasOut()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase pub = null;
        try {
            pub = ctx.createSocket(ZMQ.ZMQ_SUB);

            int events = pub.getSocketOpt(ZMQ.ZMQ_EVENTS);
            assertThat(events, is(0));
        }
        finally {
            ZMQ.close(pub);
            ZMQ.term(ctx);
        }
    }

    @Test
    public void testSetNullOption()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase pub = null;
        try {
            pub = ctx.createSocket(ZMQ.ZMQ_SUB);

            boolean rc = pub.setSocketOpt(ZMQ.ZMQ_SUBSCRIBE, null);
            assertThat(rc, is(false));
        }
        catch (IllegalArgumentException e) {
            assertThat(pub.errno.get(), is(ZError.EINVAL));
        }
        finally {
            ZMQ.close(pub);
            ZMQ.term(ctx);
        }
    }

    @Test
    public void testSend()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase pub = null;
        try {
            pub = ctx.createSocket(ZMQ.ZMQ_SUB);

            pub.send(new Msg(), ZMQ.ZMQ_DONTWAIT);
            Assert.fail("Sub cannot send message");
        }
        catch (UnsupportedOperationException e) {
            assertThat(ctx.errno().get(), is(ZError.ENOTSUP));
        }
        finally {
            ZMQ.close(pub);
            ZMQ.term(ctx);
        }
    }
}
