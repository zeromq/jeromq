package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TestHwm
{
    @Test
    public void testHwm()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        int rc;
        boolean brc;
        //  Create pair of socket, each with high watermark of 2. Thus the total
        //  buffer space should be 4 messages.
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(sb, notNullValue());
        int hwm = 2;
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVHWM, hwm);

        brc = ZMQ.bind(sb, "inproc://a");
        assertThat(brc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(sc, notNullValue());

        ZMQ.setSocketOption(sc, ZMQ.ZMQ_SNDHWM, hwm);

        brc = ZMQ.connect(sc, "inproc://a");
        assertThat(brc, is(true));

        //  Try to send 10 messages. Only 4 should succeed.
        for (int i = 0; i < 10; i++) {
            rc = ZMQ.send(sc, null, 0, ZMQ.ZMQ_DONTWAIT);
            if (i < 4) {
                assertThat(rc, is(0));
            }
            else {
                assertThat(rc, is(-1));
            }
        }

        Msg m;
        // There should be now 4 messages pending, consume them.
        for (int i = 0; i != 4; i++) {
            m = ZMQ.recv(sb, 0);
            assertThat(m, notNullValue());
            assertThat(m.size(), is(0));
        }

        // Now it should be possible to send one more.
        rc = ZMQ.send(sc, null, 0, 0);
        assertThat(rc, is(0));

        //  Consume the remaining message.
        m = ZMQ.recv(sb, 0);
        assertThat(rc, notNullValue());
        assertThat(m.size(), is(0));

        ZMQ.close(sc);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
