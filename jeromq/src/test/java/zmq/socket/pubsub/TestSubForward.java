package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class TestSubForward
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testSubForward() throws IOException
    {
        int port1 = Utils.findOpenPort();
        int port2 = Utils.findOpenPort();

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  First, create an intermediate device
        SocketBase xpubBind = ZMQ.socket(ctx, ZMQ.ZMQ_XPUB);
        assertThat(xpubBind, notNullValue());

        boolean rc = ZMQ.bind(xpubBind, "tcp://127.0.0.1:" + port1);

        SocketBase xsubBind = ZMQ.socket(ctx, ZMQ.ZMQ_XSUB);
        assertThat(xsubBind, notNullValue());

        rc = ZMQ.bind(xsubBind, "tcp://127.0.0.1:" + port2);
        assertThat(rc, is(true));

        //  Create a publisher
        SocketBase pubConnect = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(pubConnect, notNullValue());

        rc = ZMQ.connect(pubConnect, "tcp://127.0.0.1:" + port2);
        assertThat(rc, is(true));

        //  Create a subscriber
        SocketBase subConnect = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(subConnect, notNullValue());

        rc = ZMQ.connect(subConnect, "tcp://127.0.0.1:" + port1);
        assertThat(rc, is(true));

        //  Subscribe for all messages.
        ZMQ.setSocketOption(subConnect, ZMQ.ZMQ_SUBSCRIBE, "");

        ZMQ.sleep(1);

        //  Pass the subscription upstream through the device
        Msg msg = ZMQ.recv(xpubBind, 0);
        assertThat(msg, notNullValue());
        int n = ZMQ.send(xsubBind, msg, 0);
        assertThat(n, not(0));

        //  Wait a bit till the subscription gets to the publisher
        ZMQ.sleep(1);

        //  Send an empty message
        n = ZMQ.send(pubConnect, null, 0, 0);
        assertThat(n, is(0));

        //  Pass the message downstream through the device
        msg = ZMQ.recv(xsubBind, 0);
        assertThat(msg, notNullValue());
        n = ZMQ.send(xpubBind, msg, 0);
        assertThat(n, is(0));

        //  Receive the message in the subscriber
        msg = ZMQ.recv(subConnect, 0);
        assertThat(msg, notNullValue());

        //  Tear down the wiring.
        ZMQ.close(xpubBind);
        ZMQ.close(xsubBind);
        ZMQ.close(pubConnect);
        ZMQ.close(subConnect);
        ZMQ.term(ctx);
    }
}
