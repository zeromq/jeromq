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
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestReqrepTcp
{
    @Test
    public void testReqrepTcp() throws Exception
    {
        Ctx ctx = ZMQ.zmq_init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REP);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.zmq_bind(sb, "tcp://127.0.0.1:7560");
        assertThat(rc, is(true));

        SocketBase sc = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(sc, notNullValue());
        rc = ZMQ.zmq_connect(sc, "tcp://127.0.0.1:7560");
        assertThat(rc, is(true));

        Helper.bounce(sb, sc);

        ZMQ.zmq_close(sc);
        ZMQ.zmq_close(sb);
        ZMQ.zmq_term(ctx);
    }
}
