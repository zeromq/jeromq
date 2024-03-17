package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class InprocUnbindTest
{
    @Test
    public void testUnbindInproc()
    {
        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(bind, notNullValue());

        boolean rc = ZMQ.bind(bind, "inproc://a");
        assertThat(rc, is(true));

        rc = ZMQ.unbind(bind, "inproc://a");
        assertThat(rc, is(true));

        ZMQ.close(bind);

        ZMQ.term(ctx);
    }
}
