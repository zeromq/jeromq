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
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestMsgFlags {
    //  Create REQ/ROUTER wiring.
    
    @Test
    public void testMsgFlags() {
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());
        SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (sb, notNullValue());
        boolean brc = ZMQ.zmq_bind (sb, "inproc://a");
        assertThat (brc , is(true));
        
        SocketBase sc = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);
        assertThat (sc, notNullValue());
        brc = ZMQ.zmq_connect (sc, "inproc://a");
        assertThat (brc , is(true));
        
        int rc;
        //  Send 2-part message.
        rc = ZMQ.zmq_send (sc, "A", ZMQ.ZMQ_SNDMORE);
        assert (rc == 1);
        rc = ZMQ.zmq_send (sc, "B", 0);
        assert (rc == 1);

        //  Identity comes first.
        Msg msg = ZMQ.zmq_recvmsg (sb, 0);
        int more = ZMQ.zmq_msg_get (msg, ZMQ.ZMQ_MORE);
        assert (more == 1);

        //  Then the first part of the message body.
        msg = ZMQ.zmq_recvmsg (sb, 0);
        assert (rc == 1);
        more = ZMQ.zmq_msg_get (msg, ZMQ.ZMQ_MORE);
        assert (more == 1);

        //  And finally, the second part of the message body.
        msg = ZMQ.zmq_recvmsg (sb,  0);
        assert (rc == 1);
        more = ZMQ.zmq_msg_get (msg, ZMQ.ZMQ_MORE);
        assert (more == 0);

        
        //  Tear down the wiring.
        ZMQ.zmq_close (sb);
        ZMQ.zmq_close (sc);
        ZMQ.zmq_term (ctx);
    }
}
