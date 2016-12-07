package zmq;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestReqrepTcp
{
    @Test
    public void testReqrepTcp() throws Exception
    {
        int port = Utils.findOpenPort();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.bind(sb, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(sc, notNullValue());
        rc = ZMQ.connect(sc, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        Helper.bounce(sb, sc);

        ZMQ.close(sc);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
