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

public class TestTermEndpoint {

    @Test
    public void testTermEndpoint()  throws Exception {
        
        String ep = "tcp://127.0.0.1:7590";
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx!= null);

        SocketBase push = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PUSH);
        assert (push != null);
        boolean rc = ZMQ.zmq_bind (push, ep);
        assert (rc );

        SocketBase pull = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PULL);
        assert (pull != null);
        rc = ZMQ.zmq_connect (pull, ep);
        assert (rc);

    //  Pass one message through to ensure the connection is established.
        int r = ZMQ.zmq_send (push, "ABC",  0);
        assert (r == 3);
        Msg msg = ZMQ.zmq_recv (pull, 0);
        assert (msg.size() == 3);

        // Unbind the lisnening endpoint
        rc = ZMQ.zmq_unbind (push, ep);
        assert (rc);

        // Let events some time
        ZMQ.zmq_sleep (1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.zmq_send (push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assert (r == -1);

        //  Clean up.
        ZMQ.zmq_close (pull);
        ZMQ.zmq_close (push);
        ZMQ.zmq_term (ctx);


        //  Now the other way round.
        System.out.println("disconnect endpoint test running...");
        
        ctx = ZMQ.zmq_init (1);
        assert (ctx!= null);

        push = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PUSH);
        assert (push != null);
        rc = ZMQ.zmq_bind (push, ep);
        assert (rc );

        pull = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PULL);
        assert (pull != null);
        rc = ZMQ.zmq_connect (pull, ep);
        assert (rc);

        //  Pass one message through to ensure the connection is established.
        r = ZMQ.zmq_send (push, "ABC",  0);
        assert (r == 3);
        msg = ZMQ.zmq_recv (pull, 0);
        assert (msg.size() == 3);

        // Disconnect the bound endpoint
        rc = ZMQ.zmq_disconnect (push, ep);
        assert (rc);

        // Let events some time
        ZMQ.zmq_sleep (1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.zmq_send (push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assert (r == -1);

        //  Clean up.
        ZMQ.zmq_close (pull);
        ZMQ.zmq_close (push);
        ZMQ.zmq_term (ctx);
    }
}
