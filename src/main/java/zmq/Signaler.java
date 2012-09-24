/*
    Copyright (c) 2010-2011 250bpm s.r.o.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.Pipe;
import java.util.concurrent.atomic.AtomicInteger;

//  This is a cross-platform equivalent to signal_fd. However, as opposed
//  to signal_fd there can be at most one signal in the signaler at any
//  given moment. Attempt to send a signal before receiving the previous
//  one will result in undefined behaviour.

public class Signaler {
    //  Underlying write & read file descriptor.
    private Pipe.SinkChannel w;
    private Pipe.SourceChannel r;
    private Selector selector;
    private ByteBuffer rdummy;
 
    // Selector.selectNow at every sending message doesn't show enough performance
    private final AtomicInteger wcursor = new AtomicInteger(0);
    private int rcursor = 0;
    
    public Signaler() {
        //  Create the socketpair for signaling.
        make_fdpair ();

        //  Set both fds to non-blocking mode.
        try {
            Utils.unblock_socket (w);
            Utils.unblock_socket (r);
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }
        
        try {
            selector = Selector.open();
            r.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }
        
        rdummy = ByteBuffer.allocate(1);

    }
    
    public void close() {
        try {
            r.close();
            w.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //  Creates a pair of filedescriptors that will be used
    //  to pass the signals.
    private void make_fdpair() {
        Pipe pipe;
        
        try {
            pipe = Pipe.open();
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }
        r = pipe.source();
        w = pipe.sink();
    }

    public SelectableChannel get_fd() {
        return r;
    }
    
    public void send ()
    {
        
        int nbytes = 0;
        ByteBuffer dummy = ByteBuffer.allocate(1);
        dummy.limit(1);

        while (true) {
            try {
                nbytes = w.write(dummy);
            } catch (IOException e) {
                throw new ZError.IOException(e);
            }
            if (nbytes == 0) {
                continue;
            }
            wcursor.incrementAndGet();
            assert (nbytes == 1);
            break;
        }
    }

    public boolean wait_event (long timeout_) {
        
        int rc = 0;
        
        try {
            
            if (timeout_ == 0) {
                // wait_event(0) is called every read/send of SocketBase
                // instant readiness is not strictly required
                // On the other hand, we can save lots of system call and increase performance
                if (rcursor < wcursor.get()) {
                    return true;
                }
                
                return false;
            } else if (timeout_ < 0 ) {
                rc = selector.select(0);
            } else {
                rc = selector.select(timeout_);
            }
        } catch (IOException e) {
            throw new ZError.IOException(e);
        }


        if (rc == 0) {
            return false;
        }
        
        selector.selectedKeys().clear();
        
        return true;

    }


    public void recv ()
    {
        int nbytes = 0;
        while (nbytes == 0) {
            try {
                nbytes = r.read(rdummy);
                rdummy.rewind();
            } catch (IOException e) {
                throw new ZError.IOException(e);
            } 
        }
        rcursor ++;
    }
    
    


}
