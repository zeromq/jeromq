package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class TestRouterMandatory
{
    @Test
    public void testRouterMandatory() throws Exception
    {
        int rc;
        boolean brc;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sa = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        ZMQ.setSocketOption(sa, ZMQ.ZMQ_SNDHWM, 1);
        assertThat(sa, notNullValue());

        brc = ZMQ.bind(sa, "tcp://127.0.0.1:*");
        assertThat(brc, is(true));

        // Sending a message to an unknown peer with the default setting
        rc = ZMQ.send(sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(7));
        rc = ZMQ.send(sa, "DATA", 0);
        assertThat(rc, is(4));

        int mandatory = 1;

        // Set mandatory routing on socket
        ZMQ.setSocketOption(sa, ZMQ.ZMQ_ROUTER_MANDATORY, mandatory);

        // Send a message and check that it fails
        rc = ZMQ.send(sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        assertThat(rc, is(-1));
        assertThat(sa.errno(), is(ZError.EHOSTUNREACH));

        // Create a valid socket
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(sb, notNullValue());

        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVHWM, 1);
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_IDENTITY, "X");

        String host = (String) ZMQ.getSocketOptionExt(sa, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        brc = ZMQ.connect(sb, host);

        // wait until connect
        Thread.sleep(1000);

        // make it full and check that it fails
        rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.send(sa, "DATA1", 0);
        assertThat(rc, is(5));

        rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        if (rc == 1) {
            // the first frame has been sent
            rc = ZMQ.send(sa, "DATA2", 0);
            assertThat(rc, is(5));

            // send more
            rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        }
        assertThat(rc, is(-1));
        assertThat(sa.errno(), is(ZError.EAGAIN));

        //  Clean up.
        ZMQ.close(sa);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
