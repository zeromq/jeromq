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

    //  Next step. If set to NULL, it means that associated data stream
    //  is dead.
    private Object next;

    //  If true, first byte of the message is being written.
    private boolean beginning;


    //  How much data to write before next step should be executed.
    private int to_write;

    //  The buffer for encoded data.
    private ByteBuffer buf;

    private SessionBase session;
    
    protected EncoderBase (int bufsize_) {
        buf = ByteBuffer.allocateDirect(bufsize_);
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  points to NULL) decoder object will provide buffer of its own.
    //  If offset is not NULL, it is filled by offset of the first message
    //  in the batch.If there's no beginning of a message in the batch,
    //  offset is set to -1.
    
    public ByteBuffer get_data () {
        return get_data(null, null);
    }
    
    public ByteBuffer get_data (ByteBuffer data_, int[] offset_) {
        //unsigned char *buffer = !*data_ ? buf : *data_;
        //size_t buffersize = !*data_ ? bufsize : *size_;

        ByteBuffer buffer ;
        if (data_ == null) {
            buffer = buf;
            buffer.clear();
        } else {
            buffer = data_;
        }
        int buffersize = buffer.remaining();
        if (offset_ != null)
            offset_[0] = -1;

        while (true) {

            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (to_write == 0) {
                //  If we are to encode the beginning of a new message,
                //  adjust the message offset.
                if (beginning)
                    if (offset_ != null && offset_[0] == -1)
                        offset_[0] = buffer.position();

                if (!next())
                    break;
            }
            
            if (!buffer.hasRemaining()) {
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
            if (buffer.position() == 0 && data_ == null && to_write >= buffersize) {
                //ByteBuffer t = write_buf;
                //write_buf = null;
                //to_write = 0;
                //t.flip();
                return write_buf ;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int to_copy = Math.min (to_write, buffer.remaining());
            if (to_copy > 0) {
                //buffer.put(write_buf);
                int pos = buffer.position();
                write_buf.get(buffer.array(), buffer.arrayOffset() + pos, to_copy);
                buffer.position(pos + to_copy);
                to_write -= to_copy;
            }
        }

        buffer.flip();
        return buffer;

    }
    
    protected Object state () {
        return next;
    }
    
    protected void state (Object state_) {
        next = state_;
    }
    
    abstract protected boolean next();

    protected void next_step (Msg msg_, Object state_, boolean beginning_) {
        next_step(msg_.data(), msg_.size(), state_, beginning_);
    }
    
    protected void next_step (ByteBuffer buf_, int to_write_,
            Object next_, boolean beginning_)
    {
        write_buf = buf_;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }
    
    public void set_session(SessionBase session_) {
        session = session_;
    }
    
    protected Msg session_read () {
        if (session == null) {
            return null;
        }
        return session.read ();
    }

}
