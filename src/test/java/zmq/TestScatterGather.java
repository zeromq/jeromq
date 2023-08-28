package zmq;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestScatterGather
{
    @Test
    public void testTcp()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase scatter = ZMQ.socket(ctx, ZMQ.ZMQ_SCATTER);
        assertThat(scatter, notNullValue());
        boolean brc = ZMQ.bind(scatter, "tcp://127.0.0.1:*");
        assertThat(brc, is(true));

        String host = (String) ZMQ.getSocketOptionExt(scatter, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        SocketBase gather = ZMQ.socket(ctx, ZMQ.ZMQ_GATHER);
        assertThat(gather, notNullValue());
        brc = ZMQ.connect(gather, host);
        assertThat(brc, is(true));

        byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET);

        //  Send the message.
        int rc = ZMQ.send(scatter, content, 32, 0);
        assertThat(rc, is(32));

        //  Bounce the message back.
        Msg msg;
        msg = ZMQ.recv(gather, 0);
        assert (msg.size() == 32);

        //  Tear down the wiring.
        ZMQ.close(scatter);
        ZMQ.close(gather);
        ZMQ.term(ctx);
    }
}
