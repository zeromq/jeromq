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

public class TestReqrepDevice {
    //  Create REQ/ROUTER wiring.
    
    @Test
    public void testReprepDevice () {
        boolean brc;
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());
        
        //  Create a req/rep device.
        SocketBase dealer = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);
        assertThat (dealer, notNullValue());
        
        brc = ZMQ.zmq_bind (dealer, "tcp://127.0.0.1:5580");
        assertThat (brc , is(true));
        
        SocketBase router = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (router, notNullValue());
        
        brc = ZMQ.zmq_bind (router, "tcp://127.0.0.1:5581");
        assertThat (brc , is(true));
        
        //  Create a worker.
        SocketBase rep = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REP);
        assertThat (rep, notNullValue());
        
        brc = ZMQ.zmq_connect (rep, "tcp://127.0.0.1:5580");
        assertThat (brc , is(true));
        
        SocketBase req = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assertThat (req, notNullValue());
        
        brc = ZMQ.zmq_connect (req, "tcp://127.0.0.1:5581");
        assertThat (brc , is(true));
        
        //  Send a request.
        int rc;
        Msg msg;
        String buff;
        long rcvmore;
        
        rc = ZMQ.zmq_send (req, "ABC", ZMQ.ZMQ_SNDMORE);
        assertThat (rc ,is( 3));
        rc = ZMQ.zmq_send (req, "DEFG", 0);
        assertThat (rc, is( 4));
        
        //  Pass the request through the device.
        for (int i = 0; i != 4; i++) {
            
            msg = ZMQ.zmq_recvmsg (router, 0);
            assertThat (msg, notNullValue());
            rcvmore = ZMQ.zmq_getsockopt (router, ZMQ.ZMQ_RCVMORE);
            rc = ZMQ.zmq_sendmsg (dealer, msg, rcvmore >0 ? ZMQ.ZMQ_SNDMORE : 0);
            assertThat (rc >= 0, is(true));
        }
        
        //  Receive the request.
        msg = ZMQ.zmq_recv (rep, 0);
        assertThat (msg.size() , is(3));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat (buff , is("ABC"));
        rcvmore = ZMQ.zmq_getsockopt (rep, ZMQ.ZMQ_RCVMORE);
        assertThat (rcvmore>0, is(true));
        msg = ZMQ.zmq_recv (rep, 0);
        assertThat (msg.size(), is( 4));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat (buff, is("DEFG") );
        rcvmore = ZMQ.zmq_getsockopt (rep, ZMQ.ZMQ_RCVMORE);
        assertThat (rcvmore, is(0L));
        
        //  Send the reply.
        rc = ZMQ.zmq_send (rep, "GHIJKL", ZMQ.ZMQ_SNDMORE);
        assertThat (rc, is(6));
        rc = ZMQ.zmq_send (rep, "MN", 0);
        assertThat (rc , is(2));
        
        //  Pass the reply through the device.
        for (int i = 0; i != 4; i++) {
            msg = ZMQ.zmq_recvmsg (dealer, 0);
            assertThat (msg, notNullValue());
            rcvmore = ZMQ.zmq_getsockopt (dealer, ZMQ.ZMQ_RCVMORE);
            rc = ZMQ.zmq_sendmsg (router, msg, rcvmore > 0?  ZMQ.ZMQ_SNDMORE : 0);
            assertThat (rc >= 0 , is(true));
        }

    

        //  Receive the reply.
        msg = ZMQ.zmq_recv (req, 0);
        assertThat (msg.size() , is(6));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat (buff , is("GHIJKL"));
        rcvmore = ZMQ.zmq_getsockopt (req, ZMQ.ZMQ_RCVMORE);
        assertThat (rcvmore>0, is(true));
        msg = ZMQ.zmq_recv (req, 0);
        assertThat (msg.size(), is( 2));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat (buff, is("MN") );
        rcvmore = ZMQ.zmq_getsockopt (req, ZMQ.ZMQ_RCVMORE);
        assertThat (rcvmore, is(0L));
        
        //  Clean up.
        ZMQ.zmq_close (req);
        ZMQ.zmq_close (rep);
        ZMQ.zmq_close (router); 
        ZMQ.zmq_close (dealer);
        ZMQ.zmq_term (ctx);
    }
}
