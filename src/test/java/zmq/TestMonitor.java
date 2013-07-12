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

public class TestMonitor {

    static class SocketMonitor extends Thread {
        
        private Ctx ctx;
        private int events;
        private String monitor_addr;
        
        public SocketMonitor (Ctx ctx, String monitor_addr) 
        {
            this.ctx = ctx;
            this.monitor_addr = monitor_addr;
            events = 0;
        }
        @Override
        public void run () {
            
            SocketBase s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PAIR);
            boolean rc = s.connect (monitor_addr);
            assert (rc);
            // Only some of the exceptional events could fire
            while (true) {
                ZMQ.Event event = ZMQ.Event.read (s);
                if (event == null && s.errno() == ZError.ETERM)
                    break;
                assert (event != null);

                switch (event.event) {
                // listener specific
                case ZMQ.ZMQ_EVENT_LISTENING:
                    //assert (strcmp(addr, data_->listening.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_LISTENING;
                    break;
                case ZMQ.ZMQ_EVENT_ACCEPTED:
                    //assert (strcmp(addr, data_->accepted.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_ACCEPTED;
                    break;
                // connecter specific
                case ZMQ.ZMQ_EVENT_CONNECTED:
                    //assert (strcmp(addr, data_->connected.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_CONNECTED;
                    break;
                case ZMQ.ZMQ_EVENT_CONNECT_DELAYED:
                    //assert (strcmp(addr, data_->connect_delayed.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_CONNECT_DELAYED;
                    break;
                // generic - either end of the socket
                case ZMQ.ZMQ_EVENT_CLOSE_FAILED:
                    //assert (strcmp(addr, data_->close_failed.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_CLOSE_FAILED;
                    break;
                case ZMQ.ZMQ_EVENT_CLOSED:
                    //assert (strcmp(addr, data_->closed.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_CLOSED;
                    break;
                case ZMQ.ZMQ_EVENT_DISCONNECTED:
                    //assert (strcmp(addr, data_->disconnected.addr) == 0);
                    events |= ZMQ.ZMQ_EVENT_DISCONNECTED;
                    break;
                default:
                    // out of band / unexpected event
                    assertTrue("Unkown Event " + event.event, true);
                }
            }
            s.close ();
        }

    }
    
    @Test
    public void testMonitor () throws Exception {
        String addr = "tcp://127.0.0.1:5590";
        SocketMonitor [] threads = new SocketMonitor [3];
        //  Create the infrastructure
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx != null);
        // set socket monitor
        SocketBase rep = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REP);
        assert (rep != null);
        try {
            ZMQ.zmq_socket_monitor (rep, addr, 0);
            assertTrue(false);
        } catch (IllegalArgumentException e) {}

        // REP socket monitor, all events
        boolean rc = ZMQ.zmq_socket_monitor (rep, "inproc://monitor.rep", ZMQ.ZMQ_EVENT_ALL);
        assertEquals (true, rc);

        threads [0] = new SocketMonitor (ctx, "inproc://monitor.rep");
        threads [0].start ();
        
        rc = ZMQ.zmq_bind (rep, addr);
        assert (rc );

        SocketBase req = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assert (req != null);

        // REQ socket monitor, all events
        rc = ZMQ.zmq_socket_monitor (req, "inproc://monitor.req", ZMQ.ZMQ_EVENT_ALL);
        assertEquals (true, rc);

        threads [1] = new SocketMonitor (ctx, "inproc://monitor.req");
        threads [1].start ();
        
        rc = ZMQ.zmq_connect (req, addr);
        assert (rc);
        
        // 2nd REQ socket
        SocketBase req2 = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assert (req2 != null);

        
        // 2nd REQ socket monitor, connected event only
        rc = ZMQ.zmq_socket_monitor (req2, "inproc://monitor.req2", ZMQ.ZMQ_EVENT_CONNECTED);
        assertEquals (true, rc);

        threads [2] = new SocketMonitor (ctx, "inproc://monitor.req2");
        threads [2].start ();
        
        rc = ZMQ.zmq_connect (req2, addr);
        assert (rc);
        
        Helper.bounce (rep, req);
        
        // Allow a window for socket events as connect can be async
        ZMQ.zmq_sleep (1);
        
        // Close the REP socket
        ZMQ.zmq_close (rep);

        // Allow some time for detecting error states
        ZMQ.zmq_sleep (1);
        // Close the REQ socket
        ZMQ.zmq_close (req);
        // Close the 2nd REQ socket
        ZMQ.zmq_close (req2);
        
        // Allow for closed or disconnected events to bubble up
        ZMQ.zmq_sleep (1);

        ZMQ.zmq_term (ctx);

        // Expected REP socket events
        // We expect to at least observe these events
        assertTrue ((threads[0].events & ZMQ.ZMQ_EVENT_LISTENING)  > 0);
        assertTrue ((threads[0].events & ZMQ.ZMQ_EVENT_ACCEPTED) > 0);
        assertTrue ((threads[0].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);
        
        // Expected REQ socket events
        assertTrue ((threads[1].events & ZMQ.ZMQ_EVENT_CONNECTED)  > 0);
        assertTrue ((threads[1].events & ZMQ.ZMQ_EVENT_DISCONNECTED) > 0);
        assertTrue ((threads[1].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);
        
        // Expected 2nd REQ socket events
        assertTrue ((threads[2].events & ZMQ.ZMQ_EVENT_CONNECTED) > 0);
        assertTrue ((threads[2].events & ZMQ.ZMQ_EVENT_CLOSED) == 0);

        threads[0].join ();
        threads[1].join ();
        threads[2].join ();
    }
}
