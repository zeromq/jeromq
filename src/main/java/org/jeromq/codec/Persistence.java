/*
    Copyright other contributors as noted in the AUTHORS file.
                
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
package org.jeromq.codec;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import zmq.EncoderBase;
import zmq.IMsgSource;
import zmq.Msg;
import zmq.V1Protocol;

public class Persistence {

    public static final int MESSAGE_RESPONSE = 1;
    public static final int MESSAGE_FILE = 2;
    public static final int MESSAGE_ERROR = 3;
    
    public static final int STATUS_OK = 0;
    public static final int STATUS_INTERNAL_ERROR = -1;
    
    public static class PersistEncoder extends EncoderBase {

        private static final int identity_ready = 0;
        private static final int size_ready = 1;
        private static final int type_ready = 2;
        private static final int status_ready = 3;
        private static final int message_ready = 4;
        
        private Msg in_progress;
        private final byte [] tmpbuf;
        private IMsgSource msg_source;
        private final int version;
        private FileChannel channel;
        private long channel_position;
        private long channel_count;
        private int type;
        private int status;
        private boolean channel_ready;
        
        public PersistEncoder (int bufsize_)
        {
            this (bufsize_, null, 0);
        }
        
        public PersistEncoder (int bufsize_, IMsgSource session, int version)
        {
            super (bufsize_);
            this.version = version;
            tmpbuf = new byte [10];
            msg_source = session;
            
            //  Write 0 bytes to the batch and go to file_ready state.
            next_step ((byte []) null, 0, identity_ready, true);
        }

        @Override
        public void set_msg_source (IMsgSource msg_source_)
        {
            msg_source = msg_source_;
        }

        @Override
        protected boolean next () 
        {
            switch (state ()) {
            case identity_ready:
                return identity_ready ();
            case size_ready:
                return size_ready ();
            case type_ready:
                return type_ready ();
            case status_ready:
                return status_ready ();
            case message_ready:
                return message_ready ();
            default:
                return false;
            }
        }

        private final boolean size_ready ()
        {
            //  Write message body into the buffer.
            if (channel_ready)
                next_step (channel, channel_position, channel_count, type_ready, false);
            else {
                boolean more = in_progress.has_more();
                next_step (in_progress.data (), in_progress.size (),
                        more ? message_ready : type_ready, !more);
            }
            return true;
        }

        private final boolean identity_ready ()
        {
            if (msg_source == null)
                return false;
            
            if (!require_msg ())
                return false;
            
            return encode_message ();
        }
        
        private final boolean type_ready ()
        {
            //  Read new message. If there is none, return false.
            //  The first frame of the persistence codec is message type
            //  if type == MESSAGE_ERROR then close connection
            //  if type == MESSAGE_RESPONSE then transfer them all
            //  if type == MESSAGE_FILE then transfer file channel
            
            channel_ready = false;
            if (!require_msg ())
                return false;
            
            type = in_progress.data () [0];
            
            next_step ((byte []) null, 0, status_ready, true);
            return true;
        }
        
        private final boolean status_ready ()
        {
            if (!require_msg ())
                return false;
            
            status = in_progress.data () [0];
            if (type == MESSAGE_FILE) {
                process_file ();
                
                in_progress = new Msg (1);
                in_progress.set_flags (Msg.more);
                in_progress.put ((byte) status);
            }
            
            return encode_message ();
        }
        
        private final boolean message_ready ()
        {
            if (type == MESSAGE_ERROR) {
                encoding_error ();
                return false;
            }
                
            if (type == MESSAGE_FILE) {
                return encode_file ();
            } 
            
            if (!require_msg ())
                return false;

            return encode_message ();
        }

        private final void process_file () 
        {
            if (status != STATUS_OK)
                return;
            
            // The second file frame is path
            boolean rc = require_msg ();
            assert (rc);
            String path = new String (in_progress.data ());

            // The third file frame is position
            rc = require_msg ();
            assert (rc);
            channel_position = in_progress.buf ().getLong ();

            // The fourth file frame is sending count
            rc = require_msg ();
            assert (rc);
            channel_count = in_progress.buf ().getLong ();

            try {
                channel =  new RandomAccessFile (path, "r").getChannel ();
            } catch (IOException e) {
                e.printStackTrace();
                status = STATUS_INTERNAL_ERROR;
                type = MESSAGE_ERROR;
            }
        }
        
        private final boolean encode_file () 
        {
            assert (status == STATUS_OK);
            
            channel_ready = true;
            return encode_size ((int) channel_count, false);
        }
        
        private final boolean encode_message ()
        {
            if (version == V1Protocol.VERSION) {
                return v1_encode_message ();
            } else {
                return v0_encode_message ();
            }
        }

        private final boolean encode_size (int size, boolean more)
        {
            if (version == V1Protocol.VERSION) {
                return v1_encode_size (size, more);
            } else {
                return v0_encode_size (size, more);
            }
        }

        private boolean v1_encode_size (int size, boolean more)
        {
            int protocol_flags = 0;
            if (more)
                protocol_flags |= V1Protocol.MORE_FLAG;
            if (size > 255)
                protocol_flags |= V1Protocol.LARGE_FLAG;
            tmpbuf [0] = (byte) protocol_flags;
            
            //  Encode the message length. For messages less then 256 bytes,
            //  the length is encoded as 8-bit unsigned integer. For larger
            //  messages, 64-bit unsigned integer in network byte order is used.
            if (size > 255) {
                ByteBuffer b = ByteBuffer.wrap (tmpbuf);
                b.position (1);
                b.putLong (size);
                next_step (tmpbuf, 9, size_ready, false);
            }
            else {
                tmpbuf [1] = (byte) (size);
                next_step (tmpbuf, 2, size_ready, false);
            }
            return true;
        }
        private boolean v1_encode_message ()
        {
            final int size = in_progress.size ();
            
            v1_encode_size (size, in_progress.has_more ());
            return true;
        }
        
        private boolean v0_encode_size (int size, boolean more)
        {
            // Not implemented yet
            encoding_error ();
            return false;
        }
        
        private boolean v0_encode_message ()
        {
            // Not implemented yet
            encoding_error ();
            return false;
        }
        
        private boolean require_msg () {
            
            in_progress = msg_source.pull_msg ();
            if (in_progress == null) {
                return false;
            }
            return true;
        }
        

    }
}
