package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.UUID;

import org.junit.Test;

import zmq.util.Utils;

public class TestLastEndpoint
{
    static void bindAndVerify(SocketBase s, String endpoint)
    {
        boolean brc = ZMQ.bind(s, endpoint);
        assertThat(brc, is(true));

        String stest = (String) ZMQ.getSocketOptionExt(s, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(stest, is(endpoint));
    }

    @Test
    public void testLastEndpoint() throws IOException
    {
        int port1 = Utils.findOpenPort();
        int port2 = Utils.findOpenPort();

        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(sb, notNullValue());

        bindAndVerify(sb, "tcp://127.0.0.1:" + port1);
        bindAndVerify(sb, "tcp://127.0.0.1:" + port2);
        bindAndVerify(sb, "ipc:///tmp/test-" + UUID.randomUUID().toString());

        sb.close();
        ctx.terminate();
    }

    @Test
    public void testLastEndpointWildcard() throws IOException
    {
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase socket = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(socket, notNullValue());

        boolean brc = ZMQ.bind(socket, "tcp://127.0.0.1:*");
        assertThat(brc, is(true));

        String stest = (String) ZMQ.getSocketOptionExt(socket, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(stest, is(not("tcp://127.0.0.1:*")));
        assertThat(stest, is(not("tcp://127.0.0.1:0")));
        assertThat(stest.matches("tcp://127.0.0.1:\\d+"), is(true));

        socket.close();
        ctx.terminate();
    }

    @Test
    public void testLastEndpointAllWildcards() throws IOException
    {
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase socket = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(socket, notNullValue());

        boolean brc = ZMQ.bind(socket, "tcp://*:*");
        assertThat(brc, is(true));

        String stest = (String) ZMQ.getSocketOptionExt(socket, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(stest, is(not("tcp://0.0.0.0:0")));
        assertThat(stest.matches("tcp://0.0.0.0:\\d+"), is(true));
        socket.close();
        ctx.terminate();
    }
}
