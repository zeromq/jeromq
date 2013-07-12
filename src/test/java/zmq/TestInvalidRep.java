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

public class TestInvalidRep {
    //  Create REQ/ROUTER wiring.
    
    @Test
    public void testInvalidRep () {
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());
        SocketBase router_socket = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (router_socket, notNullValue());
        
        SocketBase req_socket = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assertThat (req_socket, notNullValue());
        int linger = 0;
        int rc;
        ZMQ.zmq_setsockopt (router_socket, ZMQ.ZMQ_LINGER, linger);
        ZMQ.zmq_setsockopt (req_socket, ZMQ.ZMQ_LINGER, linger);
        boolean brc = ZMQ.zmq_bind (router_socket, "inproc://hi");
        assertThat (brc , is(true));
        brc = ZMQ.zmq_connect (req_socket, "inproc://hi");
        assertThat (brc, is(true) );
    
        //  Initial request.
        rc = ZMQ.zmq_send (req_socket, "r", 0);
        assertThat (rc, is( 1));
    
        //  Receive the request.
        Msg addr ;
        Msg bottom ;
        Msg body ;
        addr = ZMQ.zmq_recv (router_socket, 0);
        int addr_size = addr.size();
        System.out.println("addr_size: " + addr.size());
        assertThat (addr.size() > 0, is(true));
        bottom = ZMQ.zmq_recv (router_socket,  0);
        assertThat (bottom.size(), is(0));
        body = ZMQ.zmq_recv (router_socket,  0);
        assertThat (body.size(), is(1));
        assertThat (body.data()[0], is((byte)'r'));
        
        //  Send invalid reply.
        rc = ZMQ.zmq_send (router_socket, addr, 0);
        assertThat (rc, is(addr_size));
        //  Send valid reply.
        rc = ZMQ.zmq_send (router_socket, addr, ZMQ.ZMQ_SNDMORE);
        assertThat (rc, is(addr_size));
        rc = ZMQ.zmq_send (router_socket, bottom, ZMQ.ZMQ_SNDMORE);
        assertThat (rc, is( 0));
        rc = ZMQ.zmq_send (router_socket, "b", 0);
        assertThat (rc, is( 1));
    
        
        //  Check whether we've got the valid reply.
        body = ZMQ.zmq_recv (req_socket, 0);
        assertThat (body.size(), is( 1));
        assertThat (body.data()[0] , is((byte)'b'));
    
        //  Tear down the wiring.
        ZMQ.zmq_close (router_socket);
        ZMQ.zmq_close (req_socket);
        ZMQ.zmq_term (ctx);
    }
}
