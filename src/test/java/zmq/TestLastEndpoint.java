package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        sb.close();
        ctx.terminate();
    }

    @Test
    public void testLastEndpointWildcardIpc()
    {
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase socket = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(socket, notNullValue());

        boolean brc = ZMQ.bind(socket, "ipc://*");
        assertThat(brc, is(true));

        String stest = (String) ZMQ.getSocketOptionExt(socket, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(stest, is(not("ipc://*")));
        assertThat(stest, is(not("ipc://localhost:0")));

        Pattern pattern = Pattern.compile("ipc://.*", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(stest);
        assertThat(stest, matcher.matches(), is(true));

        socket.close();
        ctx.terminate();
    }

    @Test
    public void testLastEndpointWildcardTcp()
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
    public void testLastEndpointAllWildcards()
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
