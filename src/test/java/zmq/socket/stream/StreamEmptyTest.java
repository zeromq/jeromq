package zmq.socket.stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class StreamEmptyTest
{
    @Test
    public void testStreamEmpty() throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://localhost:" + port;

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_STREAM);
        assert (bind != null);

        boolean rc = ZMQ.bind(bind, host);
        assert (rc);

        //  Set up connection stream.
        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assert (connect != null);

        //  Do the connection.
        rc = ZMQ.connect(connect, host);
        assert (rc);

        ZMQ.sleep(1);

        int ret = ZMQ.send(connect, "", 0);
        assertThat(ret, is(0));

        Msg msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(5));

        ret = ZMQ.send(bind, msg, ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(5));

        ret = ZMQ.send(bind, new Msg(), 0);
        assertThat(ret, is(0));

        ZMQ.setSocketOption(bind, ZMQ.ZMQ_LINGER, 0);
        ZMQ.setSocketOption(connect, ZMQ.ZMQ_LINGER, 0);

        ZMQ.close(bind);
        ZMQ.close(connect);

        ZMQ.term(ctx);
    }
}
