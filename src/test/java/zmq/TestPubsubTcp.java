package zmq;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestPubsubTcp
{
    @Test
    public void testPubsubTcp() throws Exception
    {
        int port = Utils.findOpenPort();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.bind(sb, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(sc, notNullValue());

        sc.setSocketOpt(ZMQ.ZMQ_SUBSCRIBE, "topic");

        rc = ZMQ.connect(sc, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        ZMQ.sleep(2);

        sb.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topix defg".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topic defgh".getBytes(ZMQ.CHARSET)), 0);

        Msg msg = sc.recv(0);
        assertThat(msg.size(), is(9));

        msg = sc.recv(0);
        assertThat(msg.size(), is(11));

        ZMQ.close(sc);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
