package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZMQ;

public class XPubTest
{
    @Test
    public void testSetVerbose()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase pub = null;
        try {
            pub = ctx.createSocket(ZMQ.ZMQ_XPUB);

            boolean rc = pub.setSocketOpt(ZMQ.ZMQ_XPUB_VERBOSE, 0);
            assertThat(rc, is(true));
        }
        finally {
            ZMQ.close(pub);
            ZMQ.term(ctx);
        }
    }

    @Test
    public void testSetNoDrop()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase pub = null;
        try {
            pub = ctx.createSocket(ZMQ.ZMQ_XPUB);

            boolean rc = pub.setSocketOpt(ZMQ.ZMQ_XPUB_NODROP, 0);
            assertThat(rc, is(true));
        }
        finally {
            ZMQ.close(pub);
            ZMQ.term(ctx);
        }
    }
}
