package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.util.Utils;

public class TestTermEndpoint
{
    protected String endpointWildcard()
    {
        return "tcp://127.0.0.1:" + '*';
    }

    protected String endpointNormal() throws IOException
    {
        int port = Utils.findOpenPort();
        return "tcp://127.0.0.1:" + port;
    }

    @Test(timeout = 5000)
    public void testUnbindWildcard()
    {
        String ep = endpointWildcard();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        ep = (String) ZMQ.getSocketOptionExt(push, ZMQ.ZMQ_LAST_ENDPOINT);

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC", 0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Unbind the lisnening endpoint
        rc = ZMQ.unbind(push, ep);
        assertThat(rc, is(true));

        //  Check that sending would block (there's no outbound connection).
        //  There's no way to do this except with a sleep and a loop
        while (ZMQ.send(push, "ABC", ZMQ.ZMQ_DONTWAIT) != -1) {
            ZMQ.sleep(2);
        }

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }

    @Test(timeout = 5000)
    public void testDisconnectWildcard()
    {
        String ep = endpointWildcard();

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        ep = (String) ZMQ.getSocketOptionExt(push, ZMQ.ZMQ_LAST_ENDPOINT);

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC", 0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Disconnect the bound endpoint
        rc = ZMQ.disconnect(push, ep);
        assertThat(rc, is(true));

        //  Check that sending would block (there's no outbound connection).
        //  There's no way to do this except with a sleep and a loop
        while (ZMQ.send(push, "ABC", ZMQ.ZMQ_DONTWAIT) != -1) {
            ZMQ.sleep(2);
        }

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }

    @Test
    public void testUnbindWildcardByWildcard()
    {
        String ep = endpointWildcard();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        // Unbind the lisnening endpoint
        rc = ZMQ.unbind(push, ep);
        assertThat(rc, is(false));
        assertThat(push.errno.get(), is(ZError.ENOENT));

        //  Clean up.
        ZMQ.close(push);
        ZMQ.term(ctx);
    }

    @Test
    public void testDisconnectWildcardByWildcard()
    {
        String ep = endpointWildcard();

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        // Disconnect the bound endpoint
        rc = ZMQ.disconnect(push, ep);
        assertThat(rc, is(false));
        assertThat(push.errno.get(), is(ZError.ENOENT));

        //  Clean up.
        ZMQ.close(push);
        ZMQ.term(ctx);
    }

    @Test(timeout = 5000)
    public void testUnbind() throws Exception
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());

        String ep;
        boolean rc;

        do {
            ep = endpointNormal();
            // we might have to repeat until we find an open port
            rc = ZMQ.bind(push, ep);
        } while (!rc && push.errno() == ZError.EADDRINUSE);

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC", 0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Unbind the lisnening endpoint
        rc = ZMQ.unbind(push, ep);
        assertThat(rc, is(true));

        //  Check that sending would block (there's no outbound connection).
        //  There's no way to do this except with a sleep and a loop
        while (ZMQ.send(push, "ABC", ZMQ.ZMQ_DONTWAIT) != -1) {
            ZMQ.sleep(2);
        }

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }

    @Test(timeout = 5000)
    public void testDisconnect() throws Exception
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());

        String ep;
        boolean rc;

        do {
            ep = endpointNormal();
            // we might have to repeat until we find an open port
            rc = ZMQ.bind(push, ep);
        } while (!rc && push.errno() == ZError.EADDRINUSE);

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC", 0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Disconnect the bound endpoint
        rc = ZMQ.disconnect(push, ep);
        assertThat(rc, is(true));

        //  Check that sending would block (there's no outbound connection).
        //  There's no way to do this except with a sleep and a loop
        while (ZMQ.send(push, "ABC", ZMQ.ZMQ_DONTWAIT) != -1) {
            ZMQ.sleep(2);
        }

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }
}
