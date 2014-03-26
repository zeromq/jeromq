/*
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2011 iMatix Corporation
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file

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
import static org.hamcrest.CoreMatchers.not;
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
            ZMQ.zmq_sleep(1);
            SocketBase sc = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_PUSH);
            assertThat(sc, notNullValue());
            boolean rc = ZMQ.zmq_connect(sc, "inproc://timeout_test");
            assertThat(rc, is(true));
            ZMQ.zmq_sleep(1);
            ZMQ.zmq_close(sc);
        }
    }

    @Test
    public void testTimeo() throws Exception
    {
        Ctx ctx = ZMQ.zmq_init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.zmq_bind(sb, "inproc://timeout_test");
        assertThat(rc, is(true));

        //  Check whether non-blocking recv returns immediately.
        Msg msg = ZMQ.zmq_recv(sb, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, nullValue());

        //  Check whether recv timeout is honoured.
        int timeout = 500;
        ZMQ.zmq_setsockopt(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        long watch = ZMQ.zmq_stopwatch_start();
        msg = ZMQ.zmq_recv(sb, 0);
        assertThat(msg , nullValue());
        long elapsed = ZMQ.zmq_stopwatch_stop(watch);
        assertThat(elapsed > 440000 && elapsed < 550000, is(true));

        //  Check whether connection during the wait doesn't distort the timeout.
        timeout = 2000;
        ZMQ.zmq_setsockopt(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        Thread thread = new Thread(new Worker(ctx));
        thread.start();

        watch = ZMQ.zmq_stopwatch_start();
        msg = ZMQ.zmq_recv(sb, 0);
        assertThat(msg , nullValue());
        elapsed = ZMQ.zmq_stopwatch_stop(watch);
        assertThat(elapsed > 1900000 && elapsed < 2100000, is(true));
        thread.join();

        //  Check that timeouts don't break normal message transfer.
        SocketBase sc = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(sc, notNullValue());
        ZMQ.zmq_setsockopt(sb, ZMQ.ZMQ_RCVTIMEO, timeout);
        ZMQ.zmq_setsockopt(sb, ZMQ.ZMQ_SNDTIMEO, timeout);
        rc = ZMQ.zmq_connect(sc, "inproc://timeout_test");
        assertThat(rc, is(true));
        Msg smsg = new Msg("12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET));
        int r = ZMQ.zmq_send(sc, smsg, 0);
        assertThat(r, is(32));
        msg = ZMQ.zmq_recv(sb, 0);
        assertThat(msg.size(), is(32));

        ZMQ.zmq_close(sc);
        ZMQ.zmq_close(sb);
        ZMQ.zmq_term(ctx);
    }
}
