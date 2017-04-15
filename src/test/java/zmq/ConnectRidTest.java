package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.util.Utils;

public class ConnectRidTest
{
    @Test
    public void testStream2stream() throws IOException, InterruptedException
    {
        System.out.println("Test Stream 2 stream");
        int port = Utils.findOpenPort();
        String host = "tcp://localhost:" + port;

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
    public void testRouter2routerNamed() throws IOException, InterruptedException
    {
        System.out.println("Test Router 2 Router named");
        testRouter2router(true);
    }

    @Test
    public void testRouter2routerUnnamed() throws IOException, InterruptedException
    {
        System.out.println("Test Router 2 Router unnamed");
        testRouter2router(false);
    }

    private void testRouter2router(boolean named) throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://localhost:" + port;

        Msg msg = new Msg("hi 1".getBytes(ZMQ.CHARSET));

        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assert (bind != null);

        ZMQ.setSocketOption(bind, ZMQ.ZMQ_LINGER, 0);

        boolean rc = ZMQ.bind(bind, host);
        assert (rc);

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

        Msg recv = null;
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
}
