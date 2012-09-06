/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2011 VMware, Inc.
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

abstract public class EncoderBase {

    //  Where to get the data to write from.
    private ByteBuffer write_buf;
    private byte[] write_array;
    private int write_pos;

    //  Next step. If set to -1, it means that associated data stream
    //  is dead.
    private int next;

    //  If true, first byte of the message is being written.
    @SuppressWarnings("unused")
    private boolean beginning;


    //  How much data to write before next step should be executed.
    private int to_write;

    //  The buffer for encoded data.
    private ByteBuffer buf;

    protected SessionBase session;
    
    private boolean error;
    
    protected EncoderBase (int bufsize_) {
        buf = ByteBuffer.allocateDirect(bufsize_);
        error = false;
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  points to NULL) decoder object will provide buffer of its own.
    //  If offset is not NULL, it is filled by offset of the first message
    //  in the batch.If there's no beginning of a message in the batch,
    //  offset is set to -1.
    
    protected Transfer get_data () {
        
        ByteBuffer buffer = buf;
        buffer.clear();

        int buffersize = buffer.remaining();

        int pos = 0;
        while (pos < buffersize) {

            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (to_write == 0) {
                //  If we are to encode the beginning of a new message,
                //  adjust the message offset.

                if (!next())
                    break;
            }
            
            //  If there are no data in the buffer yet and we are able to
            //  fill whole buffer in a single go, let's use zero-copy.
            //  There's no disadvantage to it as we cannot stuck multiple
            //  messages into the buffer anyway. Note that subsequent
            //  write(s) are non-blocking, thus each single write writes
            //  at most SO_SNDBUF bytes at once not depending on how large
            //  is the chunk returned from here.
            //  As a consequence, large messages being sent won't block
            //  other engines running in the same I/O thread for excessive
            //  amounts of time.
            if (buffer.position() == 0 && to_write >= buffersize) {
                Transfer t;
                if (write_array != null) {
                    ByteBuffer b = ByteBuffer.wrap(write_array);
                    b.position(write_pos);
                    t = new Transfer.ByteBufferTransfer(b);
                } else { 
                    t = new Transfer.ByteBufferTransfer(write_buf) ;
                }
                write_pos = 0;
                to_write = 0;

                return t;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int to_copy = Math.min (to_write, buffersize - pos);
            if (to_copy > 0) {
                if (write_array != null) {
                    buffer.put(write_array, write_pos, to_copy);
                } else {
                    buffer.put(write_buf.array(), write_buf.arrayOffset() + write_pos, to_copy);
                    write_buf.position(write_pos + to_copy);
                }
                pos += to_copy;
                write_pos += to_copy;
                to_write -= to_copy;
            }
        }

        buffer.flip();
        return new Transfer.ByteBufferTransfer(buffer);

    }
    
    protected int state () {
        return next;
    }
    
    protected void state (int state_) {
        next = state_;
    }
    
    
    protected void encoding_error ()
    {
        error = true;
    }
    
    public final boolean is_error() {
        return error;
    }
    
    abstract protected boolean next();

    protected void next_step (Msg msg_, int state_, boolean beginning_) {
        if (msg_ == null)
            next_step((ByteBuffer) null, 0, state_, beginning_);
        else
            next_step(msg_.data(), msg_.size(), state_, beginning_);
    }
    
    protected void next_step (ByteBuffer buf_, int to_write_,
            int next_, boolean beginning_)
    {
        
        write_buf = buf_;
        write_array = null;
        write_pos = 0;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }
    
    protected void next_step (byte[] buf_, int to_write_,
            int next_, boolean beginning_)
    {
        write_buf = null;
        write_array = buf_;
        write_pos = 0;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }
    
    public void set_session(SessionBase session_) {
        session = session_;
    }
    
}
