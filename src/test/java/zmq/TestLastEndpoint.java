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

public class TestLastEndpoint {

    static void do_bind_and_verify (SocketBase s, String endpoint)
    {
        boolean brc = ZMQ.zmq_bind (s, endpoint);
        assertThat (brc, is(true));

        String stest = (String)ZMQ.zmq_getsockoptx (s, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat (stest, is(endpoint));
    }

    @Test
    public void testLastEndpoint() {
        //  Create the infrastructure
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());

        SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (sb, notNullValue());

        do_bind_and_verify (sb, "tcp://127.0.0.1:5560");
        do_bind_and_verify (sb, "tcp://127.0.0.1:5561");
        do_bind_and_verify (sb, "ipc:///tmp/testep");
    }
}
