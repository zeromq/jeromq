package zmq.socket.stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class StreamEmptyTest
{
    @Test
    public void testStreamEmpty()
    {
        String host = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_STREAM);
        assert (bind != null);

        boolean rc = ZMQ.bind(bind, host);
        assert (rc);

        host = (String) ZMQ.getSocketOptionExt(bind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

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
