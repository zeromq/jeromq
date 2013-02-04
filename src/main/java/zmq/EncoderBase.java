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
import java.nio.channels.FileChannel;

abstract public class EncoderBase implements IEncoder {

    //  Where to get the data to write from.
    private byte[] write_buf;
    private FileChannel write_channel;
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
    
    private int buffersize;


    private boolean error;
    
    protected EncoderBase (int bufsize_) {
        buffersize = bufsize_;
        buf = ByteBuffer.allocateDirect(bufsize_);
        error = false;
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  points to NULL) decoder object will provide buffer of its own.

    @Override
    public Transfer get_data (ByteBuffer buffer) 
    {
        if (buffer == null)
            buffer = buf;
        
        buffer.clear();

        while (buffer.hasRemaining ()) {

            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (to_write == 0) {
                //  If we are to encode the beginning of a new message,
                //  adjust the message offset.

                if (!next())
                    break;
            }
            
            //  If there is file channel to send, 
            //  send current buffer and the channel together
            
            if (write_channel != null) {
                buffer.flip ();
                Transfer t = new Transfer.FileChannelTransfer (buffer, write_channel,
                                                    (long) write_pos, (long) to_write);
                write_pos = 0;
                to_write = 0;

                return t;
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
            if (buf.position () == 0 && to_write >= buffersize) {
                Transfer t;
                ByteBuffer b = ByteBuffer.wrap (write_buf);
                b.position (write_pos);
                t = new Transfer.ByteBufferTransfer (b);
                write_pos = 0;
                to_write = 0;

                return t;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int to_copy = Math.min (to_write, buffer.remaining ());
            if (to_copy > 0) {
                buffer.put (write_buf, write_pos, to_copy);
                write_pos += to_copy;
                to_write -= to_copy;
            }
        }

        buffer.flip ();
        return new Transfer.ByteBufferTransfer (buffer);

    }

    @Override
    public boolean has_data ()
    {
        return to_write > 0;
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
            next_step(null, 0, state_, beginning_);
        else
            next_step(msg_.data(), msg_.size(), state_, beginning_);
    }
    
    protected void next_step (byte[] buf_, int to_write_,
            int next_, boolean beginning_)
    {
        write_buf = buf_;
        write_channel = null;
        write_pos = 0;
        to_write = to_write_;
        next = next_;
        beginning = beginning_;
    }
    
    protected void next_step (FileChannel ch, long pos_, long to_write_,
            int next_, boolean beginning_)
    {
        write_buf = null;
        write_channel = ch;
        write_pos = (int) pos_;
        to_write = (int) to_write_;
        next = next_;
        beginning = beginning_;
    }
    
}
