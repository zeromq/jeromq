package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class RouterHandoverTest
{
    @Test
    public void testRouterHandover()
    {
        int rc;
        boolean brc;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase router = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        brc = ZMQ.bind(router, "tcp://127.0.0.1:*");
        assertThat(brc, is(true));

        // Enable the handover flag
        ZMQ.setSocketOption(router, ZMQ.ZMQ_ROUTER_HANDOVER, 1);
        assertThat(router, notNullValue());

        // Create dealer called "X" and connect it to our router
        SocketBase dealerOne = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealerOne, notNullValue());

        ZMQ.setSocketOption(dealerOne, ZMQ.ZMQ_IDENTITY, "X");

        String host = (String) ZMQ.getSocketOptionExt(router, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        brc = ZMQ.connect(dealerOne, host);
        assertThat(brc, is(true));

        // Get message from dealer to know when connection is ready
        rc = ZMQ.send(dealerOne, "Hello", 0);
        assertThat(rc, is(5));

        Msg msg = ZMQ.recv(router, 0);
        assertThat(msg.size(), is(1));
        assertThat(new String(msg.data()), is("X"));

        msg = ZMQ.recv(router, 0);
        assertThat(msg.size(), is(5));

        // Now create a second dealer that uses the same identity
        SocketBase dealerTwo = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealerTwo, notNullValue());

        ZMQ.setSocketOption(dealerTwo, ZMQ.ZMQ_IDENTITY, "X");

        brc = ZMQ.connect(dealerTwo, host);
        assertThat(brc, is(true));

        // Get message from dealer to know when connection is ready
        rc = ZMQ.send(dealerTwo, "Hello", 0);
        assertThat(rc, is(5));

        msg = ZMQ.recv(router, 0);
        assertThat(msg.size(), is(1));
        assertThat(new String(msg.data()), is("X"));

        msg = ZMQ.recv(router, 0);
        assertThat(msg.size(), is(5));

        // Send a message to 'X' identity. This should be delivered
        // to the second dealer, instead of the first because of the handover.
        rc = ZMQ.send(router, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.send(router, "Hello", 0);
        assertThat(rc, is(5));

        // Ensure that the first dealer doesn't receive the message
        // but the second one does
        msg = ZMQ.recv(dealerOne, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, nullValue());

        msg = ZMQ.recv(dealerTwo, 0);
        assertThat(msg.size(), is(5));

        //  Clean up.
        ZMQ.close(router);
        ZMQ.close(dealerOne);
        ZMQ.close(dealerTwo);
        ZMQ.term(ctx);
    }
}
