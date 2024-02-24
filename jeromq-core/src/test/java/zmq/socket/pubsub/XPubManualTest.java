package zmq.socket.pubsub;

import org.junit.Test;
import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class XPubManualTest
{
    @Test
    public void testXpubManual()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Create a publisher
        SocketBase pubBind = ZMQ.socket(ctx, ZMQ.ZMQ_XPUB);
        assertThat(pubBind, notNullValue());

        boolean rc = ZMQ.bind(pubBind, "inproc://soname");
        assertThat(rc, is(true));

        //  set pub socket options
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_XPUB_MANUAL, 1);
        int hwm = 2000;
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_SNDHWM, hwm);

        //  Create a subscriber
        SocketBase subConnect = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(subConnect, notNullValue());

        //  Subscribe for "A" messages.
        ZMQ.setSocketOption(subConnect, ZMQ.ZMQ_SUBSCRIBE, "A");

        rc = ZMQ.connect(subConnect, "inproc://soname");
        assertThat(rc, is(true));

        //  Create a subscriber
        SocketBase subConnect2 = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(subConnect2, notNullValue());

        //  Subscribe for "B" messages.
        ZMQ.setSocketOption(subConnect2, ZMQ.ZMQ_SUBSCRIBE, "B");

        rc = ZMQ.connect(subConnect2, "inproc://soname");
        assertThat(rc, is(true));

        Msg subMsg = ZMQ.recv(pubBind, ZMQ.ZMQ_DONTWAIT);
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_SUBSCRIBE, subMsg.data());

        subMsg = ZMQ.recv(pubBind, ZMQ.ZMQ_DONTWAIT);
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_SUBSCRIBE, subMsg.data());

        int hwmlimit = hwm - 1;
        int sendCount = 0;

        //  Send  message
        for (int i = 0; i < hwmlimit; i++) {
            int ret = ZMQ.send(pubBind, "A", 0);
            assert (ret == 1);
            ret = ZMQ.send(pubBind, "B", 0);
            assert (ret == 1);
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

        recvCount = 0;
        do {
            //  Receive the message in the subscriber
            msg = ZMQ.recv(subConnect2, ZMQ.ZMQ_DONTWAIT);
            if (msg != null) {
                recvCount++;
            }
        } while (msg != null);

        assertThat(sendCount, is(recvCount));

        //  Tear down the wiring.
        ZMQ.close(pubBind);
        ZMQ.close(subConnect);
        ZMQ.close(subConnect2);
        ZMQ.term(ctx);
    }
}
