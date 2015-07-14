/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package zmq;

import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestTimeo
{
    class Worker implements Runnable
    {
        Ctx ctx;
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
        assertThat(msg , nullValue());
        long elapsed = ZMQ.stopStopwatch(watch);
        assertThat(elapsed > 440000 && elapsed < 550000, is(true));

        //  Check whether connection during the wait doesn't distort the timeout.
        timeout = 2000;
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        Thread thread = new Thread(new Worker(ctx));
        thread.start();

        watch = ZMQ.startStopwatch();
        msg = ZMQ.recv(sb, 0);
        assertThat(msg , nullValue());
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
