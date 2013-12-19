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

public class Encoder extends EncoderBase {

    private final static int size_ready = 0;
    private final static int message_ready = 1;
    

    private Msg in_progress;
    private final byte[] tmpbuf;
    private IMsgSource msg_source;
    
    public Encoder (int bufsize_) 
    {
        super(bufsize_);
        tmpbuf = new byte[10];
        //  Write 0 bytes to the batch and go to message_ready state.
        next_step ((byte[])null, 0, message_ready, true);
    }

    @Override
    public void set_msg_source (IMsgSource msg_source_)
    {
        msg_source = msg_source_;
    }
    
    @Override
    protected boolean next() {
        switch(state()) {
        case size_ready:
            return size_ready ();
        case message_ready:
            return message_ready ();
        default:
            return false;
        }
    }

    
    private final boolean size_ready ()
    {
        //  Write message body into the buffer.
        next_step (in_progress.data (), in_progress.size (),
            message_ready, !in_progress.hasMore());
        return true;
    }

    
    private final boolean message_ready ()
    {
        //  Destroy content of the old message.
        //in_progress.close ();

        //  Read new message. If there is none, return false.
        //  Note that new state is set only if write is successful. That way
        //  unsuccessful write will cause retry on the next state machine
        //  invocation.
        
        if (msg_source == null)
            return false;
        
        in_progress = msg_source.pull_msg ();
        if (in_progress == null) {
            return false;
        }

        //  Get the message size.
        int size = in_progress.size ();

        //  Account for the 'flags' byte.
        size++;

        //  For messages less than 255 bytes long, write one byte of message size.
        //  For longer messages write 0xff escape character followed by 8-byte
        //  message size. In both cases 'flags' field follows.
        
        if (size < 255) {
            tmpbuf[0] = (byte)size;
            tmpbuf[1] = (byte) (in_progress.flags () & Msg.MORE);
            next_step (tmpbuf, 2, size_ready, false);
        }
        else {
            ByteBuffer b = ByteBuffer.wrap(tmpbuf);
            b.put((byte)0xff);
            b.putLong(size);
            b.put((byte) (in_progress.flags () & Msg.MORE));
            next_step (tmpbuf, 10, size_ready, false);
        }
        
        return true;
    }

}
