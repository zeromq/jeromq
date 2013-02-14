/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2009 iMatix Corporation
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

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class PollItem {

    private SocketBase s;
    private SelectableChannel c;
    private int zinterest;
    private int interest;
    private int ready;
    
    public PollItem(SocketBase s_) {
        s = s_;
        c = null;
        zinterest = interest = -1;
    }
    
    public PollItem(SocketBase s_, int ops) {
        s = s_;
        c = null;
        init (ops);
    }
    
    public PollItem(SelectableChannel c_, int ops) {
        s = null;
        c = c_;
        init (ops);
    }
    
    private void init (int ops) {
        zinterest = ops;
        int interest_ = 0;
        if ((ops & ZMQ.ZMQ_POLLIN) > 0) {
            interest_ |= SelectionKey.OP_READ ;
        }
        if ((ops & ZMQ.ZMQ_POLLOUT) > 0) {
            if (s != null)  //  ZMQ Socket get readiness from the mailbox
                interest_ |= SelectionKey.OP_READ ;
            else
                interest_ |= SelectionKey.OP_WRITE;
        }
        interest = interest_;
        ready = 0;
    }

    public final boolean isReadable() {
        return (ready & ZMQ.ZMQ_POLLIN) > 0;
    }

    public final boolean isWritable () {
        return (ready & ZMQ.ZMQ_POLLOUT) > 0;
    }
    
    public final boolean isError() {
        return (ready & ZMQ.ZMQ_POLLERR) > 0;
    }

    public final SocketBase getSocket() {
        return s;
    }

    public final SelectableChannel getRawSocket() {
        return c;
    }

    protected final SelectableChannel getChannel() {
        if (s != null)
            return s.get_fd();
        else 
            return c;
    }

    public final int interestOps() {
        return interest;
    }

    public final int interestOps(int ops) {
        init(ops);
        return interest;
    }

    public final int readyOps (SelectionKey key, int nevents) {
        ready = 0;
        
        if (s != null) {
            int events = s.getsockopt (ZMQ.ZMQ_EVENTS);
            if (events < 0)
                return -1;
            
            if ( (zinterest & ZMQ.ZMQ_POLLOUT) > 0 && (events & ZMQ.ZMQ_POLLOUT) > 0 ) {
                ready |= ZMQ.ZMQ_POLLOUT;
            }
            if ( (zinterest & ZMQ.ZMQ_POLLIN) > 0 && (events & ZMQ.ZMQ_POLLIN) > 0 ) {
                ready |= ZMQ.ZMQ_POLLIN;
            }
        } else if (nevents > 0) {
            if (key.isReadable ()) {
                ready |= ZMQ.ZMQ_POLLIN;
            }
            if (key.isWritable ()) {
                ready |= ZMQ.ZMQ_POLLOUT;
            }
            if (!key.isValid () || key.isAcceptable () || key.isConnectable ()) {
                ready |= ZMQ.ZMQ_POLLERR;
            }
        }
        
        return ready;
    }
    

    public final int readyOps() {
        return ready;
    }

}
