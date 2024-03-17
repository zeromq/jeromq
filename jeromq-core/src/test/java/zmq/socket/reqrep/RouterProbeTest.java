package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class RouterProbeTest
{
    @Test
    public void testProbeRouter() throws IOException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        Ctx ctx = ZMQ.createContext();

        //  Server socket will accept connections
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(server, notNullValue());

        boolean rc = ZMQ.bind(server, host);
        assertThat(rc, is(true));

        //  Create client and connect to server, doing a probe

        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(client, notNullValue());

        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_IDENTITY, "X");
        assertThat(rc, is(true));

        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_PROBE_ROUTER, true);
        assertThat(rc, is(true));

        rc = ZMQ.connect(client, host);
        assertThat(rc, is(true));

        //  We expect an identity=X + empty message from client
        Msg msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.get(0), is((byte) 'X'));

        msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(0));

        //  Send a message to client now
        int ret = ZMQ.send(server, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(1));

        ret = ZMQ.send(server, "Hello", 0);
        assertThat(ret, is(5));

        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(5));

        // TODO DIFF V4 test should stop here, check the logic if we should receive payload in the previous message.
        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data(), ZMQ.CHARSET), is("Hello"));

        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        //  Shutdown
        ZMQ.term(ctx);
    }
}
