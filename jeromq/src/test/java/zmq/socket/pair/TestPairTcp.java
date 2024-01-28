package zmq.socket.pair;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class TestPairTcp
{
    @Test
    public void testPairTcp() throws IOException
    {
        int port = Utils.findOpenPort();
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(sb, notNullValue());
        boolean brc = ZMQ.bind(sb, "tcp://127.0.0.1:" + port);
        assertThat(brc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(sc, notNullValue());
        brc = ZMQ.connect(sc, "tcp://127.0.0.1:" + port);
        assertThat(brc, is(true));

        Helper.bounce(sb, sc);

        //  Tear down the wiring.
        ZMQ.close(sb);
        ZMQ.close(sc);
        ZMQ.term(ctx);
    }

    @Test
    public void testPairConnectSecondClientIssue285()
    {
        String host = "tcp://127.0.0.1:*";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(bind, notNullValue());
        boolean brc = ZMQ.bind(bind, host);
        assertThat(brc, is(true));

        host = (String) ZMQ.getSocketOptionExt(bind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        SocketBase first = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(first, notNullValue());
        brc = ZMQ.connect(first, host);
        assertThat(brc, is(true));

        Helper.bounce(bind, first);

        SocketBase second = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(second, notNullValue());
        brc = ZMQ.connect(second, host);
        assertThat(brc, is(true));

        int ret = ZMQ.send(bind, "data", 0);
        assertThat(ret, is(4));
        ret = ZMQ.send(bind, "datb", 0);
        assertThat(ret, is(4));
        ret = ZMQ.send(bind, "datc", 0);
        assertThat(ret, is(4));

        ZMQ.msleep(100);

        // no receiving from second connected pair
        Msg msg = ZMQ.recv(second, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, nullValue());

        // receiving from first connected pair
        msg = ZMQ.recv(first, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, notNullValue());
        assertThat(msg.data(), is("data".getBytes(ZMQ.CHARSET)));
        msg = ZMQ.recv(first, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, notNullValue());
        assertThat(msg.data(), is("datb".getBytes(ZMQ.CHARSET)));
        msg = ZMQ.recv(first, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, notNullValue());
        assertThat(msg.data(), is("datc".getBytes(ZMQ.CHARSET)));

        //  Tear down the wiring.
        ZMQ.close(bind);
        ZMQ.close(first);
        ZMQ.close(second);
        ZMQ.term(ctx);
    }

    @Test
    public void testPairMonitorBindConnect() throws IOException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(bind, notNullValue());

        boolean rc = ZMQ.bind(bind, host);
        assertThat(rc, is(true));

        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(connect, notNullValue());
        rc = ZMQ.connect(connect, host);
        assertThat(rc, is(true));

        Helper.bounce(bind, connect);

        SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(monitor, notNullValue());

        rc = ZMQ.monitorSocket(connect, "inproc://events", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        rc = ZMQ.bind(monitor, "inproc://events");
        assertThat(rc, is(false));

        rc = ZMQ.connect(monitor, "inproc://events");
        assertThat(rc, is(true));

        //  Tear down the wiring.
        ZMQ.close(bind);
        ZMQ.close(connect);
        ZMQ.close(monitor);
        ZMQ.term(ctx);
    }

    @Test
    public void testPairMonitorIssue291() throws IOException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        // bind first to use the address
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(bind, notNullValue());
        boolean rc = ZMQ.bind(bind, host);
        assertThat(rc, is(true));

        // monitor new socket and connect another pair to send events to
        SocketBase monitored = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(monitored, notNullValue());
        rc = ZMQ.monitorSocket(monitored, "inproc://events", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(monitor, notNullValue());
        rc = ZMQ.connect(monitor, "inproc://events");
        assertThat(rc, is(true));

        // bind monitored socket with already used address
        rc = ZMQ.bind(monitored, host);
        assertThat(rc, is(false));

        //  Tear down the wiring.
        ZMQ.close(bind);
        ZMQ.close(monitored);
        ZMQ.close(monitor);
        ZMQ.term(ctx);
    }
}
