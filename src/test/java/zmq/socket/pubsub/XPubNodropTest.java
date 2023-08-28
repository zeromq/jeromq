package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class XPubNodropTest
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testXpubNoDrop()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Create a publisher
        SocketBase pubBind = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(pubBind, notNullValue());

        boolean rc = ZMQ.bind(pubBind, "inproc://soname");
        assertThat(rc, is(true));

        //  set pub socket options
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_XPUB_NODROP, 1);
        int hwm = 2000;
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_SNDHWM, hwm);

        //  Create a subscriber
        SocketBase subConnect = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(subConnect, notNullValue());

        rc = ZMQ.connect(subConnect, "inproc://soname");
        assertThat(rc, is(true));

        //  Subscribe for all messages.
        ZMQ.setSocketOption(subConnect, ZMQ.ZMQ_SUBSCRIBE, "");

        int hwmlimit = hwm - 1;
        int sendCount = 0;

        //  Send an empty message
        for (int i = 0; i < hwmlimit; i++) {
            int ret = ZMQ.send(pubBind, "", 0);
            assert (ret == 0);
            sendCount++;
        }

        int recvCount = 0;
        Msg msg;
        do {
            //  Receive the message in the subscriber
            msg = ZMQ.recv(subConnect, ZMQ.ZMQ_DONTWAIT);
            if (msg != null) {
                recvCount++;
            }
        } while (msg != null);

        assertThat(sendCount, is(recvCount));

        //  Now test real blocking behavior
        //  Set a timeout, default is infinite
        int timeout = 0;
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_SNDTIMEO, timeout);
        recvCount = 0;
        sendCount = 0;

        while (ZMQ.send(pubBind, "", 0) == 0) {
            sendCount++;
        }
        assertThat(pubBind.errno(), is(ZError.EAGAIN));
        while (ZMQ.recv(subConnect, ZMQ.ZMQ_DONTWAIT) != null) {
            recvCount++;
        }
        assertThat(sendCount, is(recvCount));

        //  Tear down the wiring.
        ZMQ.close(pubBind);
        ZMQ.close(subConnect);
        ZMQ.term(ctx);
    }
}
