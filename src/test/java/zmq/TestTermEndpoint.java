package zmq;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestTermEndpoint
{
    @Test
    public void testTermEndpoint() throws Exception
    {
        int port = Utils.findOpenPort();
        String ep = "tcp://127.0.0.1:" + port;
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC",  0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Unbind the lisnening endpoint
        rc = ZMQ.unbind(push, ep);
        assertThat(rc, is(true));

        // Let events some time
        ZMQ.sleep(1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.send(push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assertThat(r, is(-1));

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);

        //  Now the other way round.
        System.out.println("disconnect endpoint test running...");

        ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        r = ZMQ.send(push, "ABC",  0);
        assertThat(r, is(3));
        msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Disconnect the bound endpoint
        rc = ZMQ.disconnect(push, ep);
        assertThat(rc, is(true));

        // Let events some time
        ZMQ.sleep(1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.send(push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assertThat(r, is(-1));

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }
}
