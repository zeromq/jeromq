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

public class TestRouterMandatory {
    
    @Test
    public void testRouterMandatory () {
        int rc;
        boolean brc;

        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());
        
        SocketBase sa = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (sa, notNullValue());
        
        brc = ZMQ.zmq_bind (sa, "tcp://127.0.0.1:15560");
        assertThat (brc , is(true));
        
        // Sending a message to an unknown peer with the default setting
        rc = ZMQ.zmq_send (sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE);
        assertThat (rc ,is( 7));
        rc = ZMQ.zmq_send (sa, "DATA", 0);
        assertThat (rc, is( 4));
        
        int mandatory = 1;
        
        // Set mandatory routing on socket
        ZMQ.zmq_setsockopt (sa, ZMQ.ZMQ_ROUTER_MANDATORY, mandatory);
        
        // Send a message and check that it fails
        rc = ZMQ.zmq_send (sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        assertThat (rc ,is (-1));
        assertTrue (ZError.is (ZError.EHOSTUNREACH));
        
        
        //  Clean up.
        ZMQ.zmq_close (sa);
        ZMQ.zmq_term (ctx);
    }
}
