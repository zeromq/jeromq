package zmq.socket.pubsub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class TestPubsubTcp
{
    @Test
    public void testPubsubTcp()
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase pubBind = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(pubBind, notNullValue());
        ZMQ.setSocketOption(pubBind, ZMQ.ZMQ_XPUB_NODROP, true);

        boolean rc = ZMQ.bind(pubBind, "tcp://127.0.0.1:*");
        assertThat(rc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(pubBind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        SocketBase subConnect = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(subConnect, notNullValue());

        rc = subConnect.setSocketOpt(ZMQ.ZMQ_SUBSCRIBE, "topic");
        assertThat(rc, is(true));

        rc = ZMQ.connect(subConnect, host);
        assertThat(rc, is(true));

        ZMQ.sleep(1);

        System.out.print("Send");
        rc = pubBind.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));
        rc = pubBind.send(new Msg("topix defg".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));
        rc = pubBind.send(new Msg("topic defgh".getBytes(ZMQ.CHARSET)), 0);
        assertThat(rc, is(true));

        System.out.print(".Recv.");
        Msg msg = subConnect.recv(0);
        System.out.print("1.");
        assertThat(msg.size(), is(9));

        msg = subConnect.recv(0);
        System.out.print("2.");
        assertThat(msg.size(), is(11));

        System.out.print(".End.");
        ZMQ.close(subConnect);
        ZMQ.close(pubBind);
        ZMQ.term(ctx);
        System.out.println("Done.");
    }
}
