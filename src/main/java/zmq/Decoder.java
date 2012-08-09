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

public class Decoder implements IDecoder {
    
    //  Where to store the read data.
    private ByteBuffer read_buf;

    //  How much data to read before taking next step.
    private int to_read;

    //  The duffer for data to decode.
    private int bufsize;
    private ByteBuffer buf;
    
    private enum Step {
        one_byte_size_ready,
        eight_byte_size_ready,
        flags_ready,
        message_ready
    }
    private Step next;
    
    private SessionBase session;
    final private ByteBuffer tmpbuf;
    private Msg in_progress;

    private long maxmsgsize;

    public Decoder (int bufsize_, long maxmsgsize_)
    {
        next = null;
        to_read = 0;
        bufsize = bufsize_;
        session = null;
        maxmsgsize = maxmsgsize_;
        buf = ByteBuffer.allocate(bufsize_);
        tmpbuf = ByteBuffer.allocate(8);
        
    
        //  At the beginning, read one byte and go to one_byte_size_ready state.
        next_step (tmpbuf, 1, Step.one_byte_size_ready);
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
        }
        b.clear();
        return b;
    }
    

    public int process_buffer(ByteBuffer buf_) {
        //  Check if we had an error in previous attempt.
        if (next == null)
            return -1;

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (buf_ == read_buf) {
            int size_ = buf_.remaining();
            to_read -= size_;

            while (to_read == 0) {
                if (!call_next()) {
                    if (next == null)
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
                if (!call_next ()) {
                    if (next == null)
                        return -1;
                    return buf.position();
                }
            }

            //  If there are no more data in the buffer, return.
            if (!buf.hasRemaining())
                return buf.position();
            //  Copy the data from buffer to the message.
            int to_copy = Math.min (to_read, buf.remaining());
            pos = read_buf.position();
            buf.get(read_buf.array(), read_buf.arrayOffset() + pos, to_copy);
            read_buf.position(pos + to_copy);
            to_read -= to_copy;
        }
    }
    
    private void next_step (ByteBuffer buf_, int to_read_,
            Step next_)
    {
        read_buf = buf_;
        to_read = to_read_;
        next = next_;
    }
    
    private void decoding_error ()
    {
        next = null;
    }

    private boolean call_next() {
        switch(next) {
        case one_byte_size_ready:
            return one_byte_size_ready ();
        case eight_byte_size_ready:
            return eight_byte_size_ready ();
        case flags_ready:
            return flags_ready ();
        case message_ready:
            return message_ready ();
        default:
            throw new IllegalStateException(next.toString());
        }
    }



    private boolean one_byte_size_ready() {
        //  First byte of size is read. If it is 0xff read 8-byte size.
        //  Otherwise allocate the buffer for message data and read the
        //  message data into it.
        tmpbuf.flip();
        byte first = tmpbuf.get();
        if (first == 0xff) {
            
            next_step (tmpbuf, 8, Step.eight_byte_size_ready);
        } else {

            //  There has to be at least one byte (the flags) in the message).
            if (first == 0) {
                decoding_error ();
                return false;
            }

            //  in_progress is initialised at this point so in theory we should
            //  close it before calling zmq_msg_init_size, however, it's a 0-byte
            //  message and thus we can treat it as uninitialised...
            if (maxmsgsize >= 0 && (long) (first - 1) > maxmsgsize) {
                decoding_error ();
                return false;

            }
            else {
                in_progress = new Msg(first-1);
            }

            next_step (tmpbuf, 1, Step.flags_ready);
        }
        tmpbuf.clear();
        return true;

    }
    
    private boolean eight_byte_size_ready() {
        //  8-byte payload length is read. Allocate the buffer
        //  for message body and read the message data into it.
        tmpbuf.flip();
        final long payload_length = tmpbuf.getLong();

        //  There has to be at least one byte (the flags) in the message).
        if (payload_length == 0) {
            decoding_error ();
            return false;
        }

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0 && payload_length - 1 > maxmsgsize) {
            decoding_error ();
            return false;
        }

        //  Message size must fit within range of size_t data type.
        if (payload_length - 1 > Long.MAX_VALUE) {
            decoding_error ();
            return false;
        }

        final int msg_size =  (int)(payload_length - 1);
        //  in_progress is initialised at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised...
        in_progress = new Msg(msg_size);

        tmpbuf.clear();
        next_step (tmpbuf, 1, Step.flags_ready);
        
        return true;

    }
    
    private boolean flags_ready() {

        //  Store the flags from the wire into the message structure.
        
        tmpbuf.flip();
        byte first = tmpbuf.get();
        
        in_progress.set_flags (first);

        next_step (in_progress.data (), in_progress.size (),
            Step.message_ready);

        return true;

    }
    
    private boolean message_ready() {
        //  Message is completely read. Push it further and start reading
        //  new message. (in_progress is a 0-byte message after this point.)
        if (session == null)
            return false;
        
        boolean rc = session.write (in_progress);
        if (!rc) {
            decoding_error ();
            return false;
        }

        tmpbuf.clear();
        next_step (tmpbuf, 1, Step.one_byte_size_ready);
        
        return true;
    }


    
    public boolean stalled ()
    {
        return next == Step.message_ready;
    }


    public void set_session(SessionBase session_) {
        session = session_;
    }



}
