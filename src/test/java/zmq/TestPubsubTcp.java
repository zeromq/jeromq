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
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestPubsubTcp {

    @Test
    public void testPubsubTcp()  throws Exception {
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx!= null);

        SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PUB);
        assert (sb != null);
        boolean rc = ZMQ.zmq_bind (sb, "tcp://127.0.0.1:7660");
        assert (rc );

        SocketBase sc = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_SUB);
        assert (sc != null);
        
        sc.setsockopt(ZMQ.ZMQ_SUBSCRIBE, "topic");
        
        rc = ZMQ.zmq_connect (sc, "tcp://127.0.0.1:7660");
        assert (rc);

        ZMQ.zmq_sleep(2);
        
        sb.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topix defg".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topic defgh".getBytes(ZMQ.CHARSET)), 0);
        
        Msg msg = sc.recv(0);
        assertThat(msg.size(), is(9));

        msg = sc.recv(0);
        assertThat(msg.size(), is(11));
        
        ZMQ.zmq_close (sc);

        ZMQ.zmq_close (sb);
        
        ZMQ.zmq_term (ctx);
    }
}
