package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TestMsgFlags
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testMsgFlags()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(sb, notNullValue());
        boolean brc = ZMQ.bind(sb, "inproc://a");
        assertThat(brc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(sc, notNullValue());
        brc = ZMQ.connect(sc, "inproc://a");
        assertThat(brc, is(true));

        int rc;
        //  Send 2-part message.
        rc = ZMQ.send(sc, "A", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.send(sc, "B", 0);
        assertThat(rc, is(1));

        //  Identity comes first.
        Msg msg = ZMQ.recvMsg(sb, 0);
        int more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(1));

        //  Then the first part of the message body.
        msg = ZMQ.recvMsg(sb, 0);
        assertThat(rc, is(1));
        more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(1));

        //  And finally, the second part of the message body.
        msg = ZMQ.recvMsg(sb, 0);
        assertThat(rc, is(1));
        more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(0));

        //  Tear down the wiring.
        ZMQ.close(sb);
        ZMQ.close(sc);
        ZMQ.term(ctx);
    }
}
