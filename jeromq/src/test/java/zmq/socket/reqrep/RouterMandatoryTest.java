package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class RouterMandatoryTest
{
    @Test
    public void testRouterMandatory()
    {
        int sent;
        boolean rc;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase router = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(router, notNullValue());

        rc = ZMQ.bind(router, "tcp://127.0.0.1:*");
        assertThat(rc, is(true));

        // Sending a message to an unknown peer with the default setting
        //  This will not report any error
        sent = ZMQ.send(router, "UNKNOWN", ZMQ.ZMQ_SNDMORE);
        assertThat(sent, is(7));

        sent = ZMQ.send(router, "DATA", 0);
        assertThat(sent, is(4));

        //  Send a message to an unknown peer with mandatory routing
        //  This will fail
        int mandatory = 1;
        ZMQ.setSocketOption(router, ZMQ.ZMQ_ROUTER_MANDATORY, mandatory);

        // Send a message and check that it fails
        sent = ZMQ.send(router, "UNKNOWN", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        assertThat(sent, is(-1));
        assertThat(router.errno(), is(ZError.EHOSTUNREACH));

        //  Create dealer called "X" and connect it to our router
        SocketBase dealer = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());

        ZMQ.setSocketOption(dealer, ZMQ.ZMQ_IDENTITY, "X");

        String host = (String) ZMQ.getSocketOptionExt(router, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        rc = ZMQ.connect(dealer, host);
        assertThat(rc, is(true));

        //  Get message from dealer to know when connection is ready
        int ret = ZMQ.send(dealer, "Hello", 0);
        assertThat(ret, is(5));

        Msg msg = ZMQ.recv(router, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.data()[0], is((byte) 'X'));

        //  Send a message to connected dealer now
        //  It should work
        sent = ZMQ.send(router, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(sent, is(1));
        sent = ZMQ.send(router, "Hello", 0);
        assertThat(sent, is(5));

        //  Clean up.
        ZMQ.close(router);
        ZMQ.close(dealer);
        ZMQ.term(ctx);
    }

    private static final int BUF_SIZE = 65636;

    @Test
    public void testRouterMandatoryHwm()
    {
        boolean rc;

        System.out.print("Starting router mandatory HWM test");

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase router = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(router, notNullValue());

        ZMQ.setSocketOption(router, ZMQ.ZMQ_ROUTER_MANDATORY, true);
        ZMQ.setSocketOption(router, ZMQ.ZMQ_SNDHWM, 1);
        ZMQ.setSocketOption(router, ZMQ.ZMQ_LINGER, 1);

        rc = ZMQ.bind(router, "tcp://127.0.0.1:*");
        assertThat(rc, is(true));

        //  Create dealer called "X" and connect it to our router, configure HWM
        SocketBase dealer = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());

        ZMQ.setSocketOption(dealer, ZMQ.ZMQ_RCVHWM, 1);
        ZMQ.setSocketOption(dealer, ZMQ.ZMQ_IDENTITY, "X");

        String host = (String) ZMQ.getSocketOptionExt(router, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        rc = ZMQ.connect(dealer, host);
        assertThat(rc, is(true));

        System.out.print(".");
        //  Get message from dealer to know when connection is ready
        int ret = ZMQ.send(dealer, "Hello", 0);
        assertThat(ret, is(5));

        System.out.print(".");
        Msg msg = ZMQ.recv(router, 0);
        System.out.print(".");
        assertThat(msg, notNullValue());
        assertThat(msg.data()[0], is((byte) 'X'));

        int i = 0;
        for (; i < 100000; ++i) {
            ret = ZMQ.send(router, "X", ZMQ.ZMQ_DONTWAIT | ZMQ.ZMQ_SNDMORE);
            if (ret == -1 && router.errno() == ZError.EAGAIN) {
                break;
            }
            assertThat(ret, is(1));
            ret = ZMQ.send(router, new byte[BUF_SIZE], BUF_SIZE, ZMQ.ZMQ_DONTWAIT);
            assertThat(ret, is(BUF_SIZE));
        }
        System.out.print(".");

        // This should fail after one message but kernel buffering could
        // skew results
        assertThat(i < 10, is(true));

        ZMQ.sleep(1);
        // Send second batch of messages
        for (; i < 100000; ++i) {
            ret = ZMQ.send(router, "X", ZMQ.ZMQ_DONTWAIT | ZMQ.ZMQ_SNDMORE);
            if (ret == -1 && router.errno() == ZError.EAGAIN) {
                break;
            }
            assertThat(ret, is(1));
            ret = ZMQ.send(router, new byte[BUF_SIZE], BUF_SIZE, ZMQ.ZMQ_DONTWAIT);
            assertThat(ret, is(BUF_SIZE));
        }
        System.out.print(".");
        // This should fail after two messages but kernel buffering could
        // skew results
        assertThat(i < 20, is(true));
        System.out.println("Done sending messages.");
        //  Clean up.
        ZMQ.close(router);
        ZMQ.close(dealer);
        ZMQ.term(ctx);
    }
}
