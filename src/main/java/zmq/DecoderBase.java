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

import java.nio.ByteBuffer;

//  Helper base class for decoders that know the amount of data to read
//  in advance at any moment. Knowing the amount in advance is a property
//  of the protocol used. 0MQ framing protocol is based size-prefixed
//  paradigm, which qualifies it to be parsed by this class.
//  On the other hand, XML-based transports (like XMPP or SOAP) don't allow
//  for knowing the size of data to read in advance and should use different
//  decoding algorithms.
//
//  This class implements the state machine that parses the incoming buffer.
//  Derived class should implement individual state machine actions.

abstract public class DecoderBase {
    
    //  Where to store the read data.
    private ByteBuffer read_buf;

    //  How much data to read before taking next step.
    private int to_read;

    //  The buffer for data to decode.
    private int bufsize;
    private ByteBuffer buf;
    
    private Object state;
    
    private SessionBase session;

    protected long maxmsgsize;

    public DecoderBase (int bufsize_, long maxmsgsize_)
    {
        state = null;
        to_read = 0;
        bufsize = bufsize_;
        session = null;
        maxmsgsize = maxmsgsize_;
        buf = ByteBuffer.allocate(bufsize_);
    }
    
    
    //  Returns a buffer to be filled with binary data.
    public ByteBuffer get_buffer() {
        //  If we are expected to read large message, we'll opt for zero-
        //  copy, i.e. we'll ask caller to fill the data directly to the
        //  message. Note that subsequent read(s) are non-blocking, thus
        //  each single read reads at most SO_RCVBUF bytes at once not
        //  depending on how large is the chunk returned from here.
        //  As a consequence, large messages being received won't block
        //  other engines running in the same I/O thread for excessive
        //  amounts of time.
        
        ByteBuffer b;
        if (to_read >= bufsize) {
            b = read_buf;
        } else {
            b = buf;
            b.clear();
        }
        return b;
    }
    

    // return remaining
    public int process_buffer(ByteBuffer buf_) {
        //  Check if we had an error in previous attempt.
        if (state() == null)
            return -1;

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (buf_ == read_buf) {
            to_read = buf_.remaining();
            //to_read -= size_;

            while (to_read == 0) {
                if (!next()) {
                    if (state() == null)
                        return -1;
                    return 0;
                }
            }
            return 0;
        }

        buf.flip();
        int pos = 0;
        while (true) {

            //  Try to get more space in the message to fill in.
            //  If none is available, return.
            while (to_read == 0) {
                read_buf.flip();
                if (!next ()) {
                    if (state() == null)
                        return -1;
                    return buf.remaining();
                }
            }

            //  If there are no more data in the buffer, return.
            if (!buf.hasRemaining())
                return 0;
            //  Copy the data from buffer to the message.
            int to_copy = Math.min (to_read, buf.remaining());
            pos = read_buf.position();
            buf.get(read_buf.array(), read_buf.arrayOffset() + pos, to_copy);
            read_buf.position(pos + to_copy);
            to_read -= to_copy;
        }
    }
    
    protected void next_step (Msg msg_, Object state_) {
        next_step(msg_.data(), msg_.size(), state_);
    }
    
    protected void next_step (ByteBuffer buf_, int to_read_, Object state_)
    {
        read_buf = buf_;
        to_read = to_read_;
        state = state_;
        read_buf.clear();
    }
    
    protected boolean session_write (Msg msg) {
        
        if (session == null) {
            return false;
        } 
        return session.write(msg);
    }
    
    protected Object state () {
        return state;
    }
    
    protected void state (Object state_) {
        state = state_;
    }
    
    abstract protected boolean next();
    
    abstract public boolean stalled ();

    public void set_session(SessionBase session_) { 
        session = session_;
    }
    

}
