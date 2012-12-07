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

abstract public class DecoderBase implements IDecoder {
    
    //  Where to store the read data.
    private byte[] read_buf;
    private int read_pos;

    //  How much data to read before taking next step.
    protected int to_read;

    //  The buffer for data to decode.
    private int bufsize;
    private ByteBuffer buf;
    
    private int state;

    boolean zero_copy;
    
    public DecoderBase (int bufsize_)
    {
        state = -1;
        to_read = 0;
        bufsize = bufsize_;
        if (bufsize_ > 0)
            buf = ByteBuffer.allocateDirect (bufsize_);
        read_buf = null;
        zero_copy = false;
    }
    
    
    //  Returns a buffer to be filled with binary data.
    public ByteBuffer get_buffer () 
    {
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
            zero_copy = true;
            b = ByteBuffer.wrap (read_buf);
            b.position (read_pos);
        } else {
            zero_copy = false;
            b = buf;
            b.clear();
        }
        return b;
    }
    

    //  Processes the data in the buffer previously allocated using
    //  get_buffer function. size_ argument specifies nemuber of bytes
    //  actually filled into the buffer. Function returns number of
    //  bytes actually processed.
    public int process_buffer(ByteBuffer buf_, int size_) {
        //  Check if we had an error in previous attempt.
        if (state() < 0)
            return -1;

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (zero_copy) {
            read_pos += size_;
            to_read -= size_;

            while (to_read == 0) {
                if (!next()) {
                    if (state() < 0)
                        return -1;
                    return size_;
                }
            }
            return size_;
        }

        int pos = 0;
        while (true) {

            //  Try to get more space in the message to fill in.
            //  If none is available, return.
            while (to_read == 0) {
                if (!next ()) {
                    if (state() < 0) {
                        return -1;
                    }

                    return pos;
                }
            }

            //  If there are no more data in the buffer, return.
            if (pos == size_)
                return pos;
            
            //  Copy the data from buffer to the message.
            int to_copy = Math.min (to_read, size_ - pos);
            buf_.get(read_buf, read_pos, to_copy);
            read_pos += to_copy;
            pos += to_copy;
            to_read -= to_copy;
        }
    }
    

    protected void next_step (Msg msg_, int state_) {
        next_step(msg_.data(), msg_.size(), state_);
    }
    
    protected void next_step (byte[] buf_, int to_read_, int state_)
    {
        read_buf = buf_;
        read_pos = 0;
        to_read = to_read_;
        state = state_;
    }
    
    protected int state () {
        return state;
    }
    
    protected void state (int state_) {
        state = state_;
    }
    
    
    protected void decoding_error ()
    {
        state(-1);
    }

    //  Returns true if the decoder has been fed all required data
    //  but cannot proceed with the next decoding step.
    //  False is returned if the decoder has encountered an error.
    @Override
    public boolean stalled ()
    {
        //  Check whether there was decoding error.
        if (!next ())
            return false;
        
        while (to_read == 0) {
            if (!next ()) {
                if (!next ())
                    return false;
                return true;
            }
        }
        return false;
    }



    abstract protected boolean next();
    
}
