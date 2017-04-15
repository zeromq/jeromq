package zmq.socket.pipeline;

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

public class TestPushPullTcp
{
    @Test
    public void testPushPullTcp() throws IOException
    {
        int port = Utils.findOpenPort();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean brc = ZMQ.bind(push, "tcp://127.0.0.1:" + port);
        assertThat(brc, is(true));

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        brc = ZMQ.connect(pull, "tcp://127.0.0.1:" + port);
        assertThat(brc, is(true));

        byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET);

        //  Send the message.
        int rc = ZMQ.send(push, content, 32, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.send(push, content, 32, 0);
        assertThat(rc, is(32));

        //  Bounce the message back.
        Msg msg;
        msg = ZMQ.recv(pull, 0);
        assert (msg.size() == 32);
        int rcvmore = ZMQ.getSocketOption(pull, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(1));

        msg = ZMQ.recv(pull, 0);
        assert (rc == 32);
        rcvmore = ZMQ.getSocketOption(pull, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(0));

        //  Tear down the wiring.
        ZMQ.close(push);
        ZMQ.close(pull);
        ZMQ.term(ctx);
    }
}
