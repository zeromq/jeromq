package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TestTimeo
{
    static class Worker implements Runnable
    {
        final Ctx ctx;

        Worker(Ctx ctx)
        {
            this.ctx = ctx;
        }

        @Override
        public void run()
        {
            ZMQ.sleep(1);
            SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
            assertThat(sc, notNullValue());
            boolean rc = ZMQ.connect(sc, "inproc://timeout_test");
            assertThat(rc, is(true));
            ZMQ.sleep(1);
            ZMQ.close(sc);
        }
    }

    @Test
    public void testTimeo() throws Exception
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.bind(sb, "inproc://timeout_test");
        assertThat(rc, is(true));

        //  Check whether non-blocking recv returns immediately.
        Msg msg = ZMQ.recv(sb, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, nullValue());

        //  Check whether recv timeout is honoured.
        int timeout = 500;
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        long watch = ZMQ.startStopwatch();
        msg = ZMQ.recv(sb, 0);
        assertThat(msg, nullValue());
        long elapsed = ZMQ.stopStopwatch(watch);
        assertThat(elapsed > 440000 && elapsed < 550000, is(true));

        //  Check whether connection during the wait doesn't distort the timeout.
        timeout = 2000;
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        Thread thread = new Thread(new Worker(ctx));
        thread.start();

        watch = ZMQ.startStopwatch();
        msg = ZMQ.recv(sb, 0);
        assertThat(msg, nullValue());
        elapsed = ZMQ.stopStopwatch(watch);
        assertThat(elapsed > 1900000 && elapsed < 2100000, is(true));
        thread.join();

        //  Check that timeouts don't break normal message transfer.
        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(sc, notNullValue());
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_SNDTIMEO, timeout);
        rc = ZMQ.connect(sc, "inproc://timeout_test");
        assertThat(rc, is(true));
        Msg smsg = new Msg("12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET));
        int r = ZMQ.send(sc, smsg, 0);
        assertThat(r, is(32));
        msg = ZMQ.recv(sb, 0);
        assertThat(msg.size(), is(32));

        ZMQ.close(sc);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
