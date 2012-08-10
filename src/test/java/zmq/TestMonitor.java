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

public class TestMonitor {

    private static int events = 0;
    
    static class SocketMonitor implements IZmqMonitor {
        
        @Override
        public void monitor(SocketBase socket_, int event_, Object[] args_) {
            
            String addr = "tcp://127.0.0.1:5560";
            assert (args_!= null);
            // Only some of the exceptional events could fire
            switch (event_) {
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
                assert (false);
            }
        }

    }
    
    @Test
    public void testMonitor () {
        //  Create the infrastructure
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx != null);
        // set socket monitor
        ZMQ.zmq_ctx_set_monitor (ctx, new SocketMonitor());
        SocketBase rep = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REP);
        assert (rep != null);

        boolean rc = ZMQ.zmq_bind (rep, "tcp://127.0.0.1:5560");
        assert (rc );

        SocketBase req = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assert (req != null);

        rc = ZMQ.zmq_connect (req, "tcp://127.0.0.1:5560");
        assert (rc);
        
        // Allow for closed or disconnected events to bubble up
        ZMQ.zmq_sleep (1);

        ZMQ.zmq_close (rep);

        ZMQ.zmq_sleep (1);

        ZMQ.zmq_term (ctx);

        // We expect to at least observe these events
        assert ((events & ZMQ.ZMQ_EVENT_LISTENING)  > 0);
        assert ((events & ZMQ.ZMQ_EVENT_ACCEPTED) > 0);
        assert ((events & ZMQ.ZMQ_EVENT_CONNECTED) > 0);
        assert ((events & ZMQ.ZMQ_EVENT_CLOSED) > 0);
        assert ((events & ZMQ.ZMQ_EVENT_DISCONNECTED) > 0);

    }
}
