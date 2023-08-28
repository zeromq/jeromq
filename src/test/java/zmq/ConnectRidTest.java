package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class ConnectRidTest
{
    @Test
    public void testStream2stream()
    {
        System.out.println("Test Stream 2 stream");
        String host = "tcp://localhost:*";

        Msg msg = new Msg("hi 1".getBytes(ZMQ.CHARSET));

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_STREAM);
        assert (bind != null);

        ZMQ.setSocketOption(bind, ZMQ.ZMQ_CONNECT_RID, "connectRid");
        ZMQ.setSocketOption(bind, ZMQ.ZMQ_LINGER, 0);

        boolean rc = ZMQ.bind(bind, host);
        assert (rc);

        host = (String) ZMQ.getSocketOptionExt(bind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        //  Set up connection stream.
        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_STREAM);
        assert (connect != null);

        ZMQ.setSocketOption(connect, ZMQ.ZMQ_LINGER, 0);

        //  Do the connection.
        ZMQ.setSocketOption(connect, ZMQ.ZMQ_CONNECT_RID, "connectRid");
        rc = ZMQ.connect(connect, host);
        assert (rc);

        ZMQ.sleep(1);

        /*  Uncomment to test assert on duplicate rid.
        //  Test duplicate connect attempt.
        ZMQ.setSocketOption(connect, ZMQ_CONNECT_RID, "conn1", 6);
        rc = ZMQ.connect(connect, host);
        assert (rc);
        */

        //  Send data to the bound stream.
        int ret = ZMQ.send(connect, "connectRid", ZMQ.ZMQ_SNDMORE);
        assert (10 == ret);
        ret = ZMQ.send(connect, msg, 0);
        assert (4 == ret);

        //  Accept data on the bound stream.
        Msg recv = ZMQ.recv(bind, 0);
        assert (recv != null);

        recv = ZMQ.recv(bind, 0);
        assert (recv != null);

        ZMQ.close(bind);
        ZMQ.close(connect);

        ZMQ.term(ctx);
    }

    @Test
    public void testRouter2routerNamed()
    {
        System.out.println("Test Router 2 Router named");
        testRouter2router(true);
    }

    @Test
    public void testRouter2routerUnnamed()
    {
        System.out.println("Test Router 2 Router unnamed");
        testRouter2router(false);
    }

    private void testRouter2router(boolean named)
    {
        String host = "tcp://localhost:*";

        Msg msg = new Msg("hi 1".getBytes(ZMQ.CHARSET));

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (bind != null);

        ZMQ.setSocketOption(bind, ZMQ.ZMQ_LINGER, 0);

        boolean rc = ZMQ.bind(bind, host);
        assert (rc);

        host = (String) ZMQ.getSocketOptionExt(bind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        //  Set up connection stream.
        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (connect != null);

        ZMQ.setSocketOption(connect, ZMQ.ZMQ_LINGER, 0);

        //  Do the connection.
        ZMQ.setSocketOption(connect, ZMQ.ZMQ_CONNECT_RID, "connectRid");
        rc = ZMQ.connect(connect, host);
        assert (rc);

        if (named) {
            ZMQ.setSocketOption(bind, ZMQ.ZMQ_IDENTITY, "X");
            ZMQ.setSocketOption(connect, ZMQ.ZMQ_IDENTITY, "Y");
        }

        ZMQ.sleep(1);

        /*  Uncomment to test assert on duplicate rid.
        //  Test duplicate connect attempt.
        ZMQ.setSocketOption(connect, ZMQ_CONNECT_RID, "conn1", 6);
        rc = ZMQ.connect(connect, host);
        assert (rc);
        */

        //  Send data to the bound stream.
        int ret = ZMQ.send(connect, "connectRid", ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(10));
        ret = ZMQ.send(connect, msg, 0);
        assertThat(ret, is(4));

        Msg recv;
        //  Receive the name.
        Msg name = ZMQ.recv(bind, 0);
        assertThat(name, notNullValue());
        assert (name != null);
        if (named) {
            assertThat(name.data()[0], is((byte) 'Y'));
        }
        else {
            assertThat(name.data()[0], is((byte) 0));
        }

        //  Receive the data.
        recv = ZMQ.recv(bind, 0);
        assertThat(recv, notNullValue());

        //  Send some data back.
        if (named) {
            ret = ZMQ.send(bind, name, ZMQ.ZMQ_SNDMORE);
            assertThat(ret, is(1));
        }
        else {
            ret = ZMQ.send(bind, name, ZMQ.ZMQ_SNDMORE);
            assertThat(ret, is(5));
        }

        ret = ZMQ.send(bind, "ok", 0);
        assertThat(ret, is(2));

        //  If bound socket identity naming a problem, we'll likely see something funky here.
        recv = ZMQ.recv(connect, 0);
        assertThat(recv, notNullValue());
        assertThat(recv.data()[0], is((byte) 'c'));

        recv = ZMQ.recv(connect, 0);
        assertThat(recv, notNullValue());
        assertThat(recv.data()[0], is((byte) 'o'));
        assertThat(recv.data()[1], is((byte) 'k'));

        ZMQ.close(bind);
        ZMQ.close(connect);

        ZMQ.term(ctx);
    }

    @Test
    public void testRouter2routerWhileReceiving()
    {
        System.out.println("Test Router 2 router while receiving");
        String wildcardAddress = "tcp://localhost:*";

        Msg msg = new Msg("hi 1".getBytes(ZMQ.CHARSET));

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up the router which both binds and connects
        SocketBase x = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (x != null);

        ZMQ.setSocketOption(x, ZMQ.ZMQ_LINGER, 0);
        ZMQ.setSocketOption(x, ZMQ.ZMQ_IDENTITY, "X");

        boolean rc = ZMQ.bind(x, wildcardAddress);
        assert (rc);

        String xaddress = (String) ZMQ.getSocketOptionExt(x, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(xaddress, notNullValue());

        //  Set up the router which binds
        SocketBase z = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (z != null);

        ZMQ.setSocketOption(z, ZMQ.ZMQ_LINGER, 0);
        ZMQ.setSocketOption(z, ZMQ.ZMQ_IDENTITY, "Z");

        rc = ZMQ.bind(z, wildcardAddress);
        assert (rc);

        String zaddress = (String) ZMQ.getSocketOptionExt(z, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(zaddress, notNullValue());

        //  Set up connect-only router.
        SocketBase y = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (y != null);

        ZMQ.setSocketOption(y, ZMQ.ZMQ_LINGER, 0);
        ZMQ.setSocketOption(y, ZMQ.ZMQ_IDENTITY, "Y");

        //  Do the connection.
        rc = ZMQ.connect(y, xaddress);
        assert (rc);

        //  Send data from Y to X
        int ret = ZMQ.send(y, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(1));
        ret = ZMQ.send(y, msg, 0);
        assertThat(ret, is(4));

        // wait for the messages to be processed on the io thread
        ZMQ.msleep(100);

        // try to connect X to Z
        ZMQ.setSocketOption(x, ZMQ.ZMQ_CONNECT_RID, "Z");
        rc = ZMQ.connect(x, zaddress);
        assert (rc);

        //  Send data from X to Z
        ret = ZMQ.send(x, "Z", ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(1));
        ret = ZMQ.send(x, msg, 0);
        assertThat(ret, is(4));

        // wait for the messages to be delivered
        ZMQ.msleep(100);

        // Make sure that Y has not received any messages
        Msg name = ZMQ.recv(y, ZMQ.ZMQ_DONTWAIT);
        assertThat(name, nullValue());

        //  Receive the message from Z
        name = ZMQ.recv(z, 0);
        assertThat(name, notNullValue());
        assertThat(name.data()[0], is((byte) 'X'));

        Msg recv;
        recv = ZMQ.recv(z, 0);
        assertThat(recv, notNullValue());
        assertThat(recv.data()[0], is((byte) 'h'));

        ZMQ.close(x);
        ZMQ.close(y);
        ZMQ.close(z);

        ZMQ.term(ctx);
    }
}
