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

public class StreamTest
{
    @Test
    public void testStream2stream() throws IOException, InterruptedException
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
        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_STREAM);
        assert (connect != null);

        //  Do the connection.
        rc = ZMQ.connect(connect, host);
        assert (rc);

        ZMQ.sleep(1);

        //  Connecting sends a zero message
        //  Server: First frame is identity, second frame is zero

        Msg msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size() > 0, is(true));

        msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(0));

        ZMQ.close(bind);
        ZMQ.close(connect);

        ZMQ.term(ctx);
    }
}
