/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2011 250bpm s.r.o.
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file

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
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestSubForward
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testSubForward()
    {
        Ctx ctx = ZMQ.zmq_init(1);
        assertThat(ctx, notNullValue());
        SocketBase xpub = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_XPUB);
        assertThat(xpub, notNullValue());
        boolean rc = ZMQ.zmq_bind(xpub, "tcp://127.0.0.1:5570");

        SocketBase xsub = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_XSUB);
        assertThat(xsub, notNullValue());
        rc = ZMQ.zmq_bind(xsub, "tcp://127.0.0.1:5571");
        assertThat(rc, is(true));

        SocketBase pub = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(pub, notNullValue());
        rc = ZMQ.zmq_connect(pub, "tcp://127.0.0.1:5571");
        assertThat(rc, is(true));

        SocketBase sub = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(sub, notNullValue());
        rc = ZMQ.zmq_connect(sub, "tcp://127.0.0.1:5570");
        assertThat(rc, is(true));

        ZMQ.zmq_setsockopt(sub, ZMQ.ZMQ_SUBSCRIBE, "");
        Msg msg = ZMQ.zmq_recv(xpub, 0);
        assertThat(msg, notNullValue());
        int n = ZMQ.zmq_send(xsub, msg, 0);
        assertThat(n, not(0));

        ZMQ.zmq_sleep(1);

        n = ZMQ.zmq_send(pub, null, 0, 0);
        assertThat(n, is(0));

        msg = ZMQ.zmq_recv(xsub, 0);
        assertThat(msg, notNullValue());

        n = ZMQ.zmq_send(xpub, msg, 0);
        assertThat(n, is(0));

        msg = ZMQ.zmq_recv(sub, 0);
        assertThat(msg, notNullValue());

        //  Tear down the wiring.
        ZMQ.zmq_close(xpub);
        ZMQ.zmq_close(xsub);
        ZMQ.zmq_close(pub);
        ZMQ.zmq_close(sub);
        ZMQ.zmq_term(ctx);
    }
}
