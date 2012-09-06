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

import java.nio.ByteBuffer;

import zmq.DecoderBase;
import zmq.Msg;

//FIXME: not fully implemented yet
public class Persistence {

    public static class PersistDecoder extends DecoderBase {
        
        private final static int one_byte_size_ready = 0;
        private final static int eight_byte_size_ready = 1;
        private final static int flags_ready = 2;
        private final static int message_ready = 3;
        
        final private ByteBuffer tmpbuf;
        private Msg in_progress;
        

        public PersistDecoder (int bufsize_, long maxmsgsize_)
        {
            super(bufsize_, maxmsgsize_);
            
            tmpbuf = ByteBuffer.allocate(8);
            
        
            //  At the beginning, read one byte and go to one_byte_size_ready state.
            next_step (tmpbuf, 1, one_byte_size_ready);
        }
        
            
        @Override
        protected boolean next() {
            switch(state()) {
            case one_byte_size_ready:
                return one_byte_size_ready ();
            case eight_byte_size_ready:
                return eight_byte_size_ready ();
            case flags_ready:
                return flags_ready ();
            case message_ready:
                return message_ready ();
            default:
                return false;
            }
        }



        private boolean one_byte_size_ready() {
            //  First byte of size is read. If it is 0xff read 8-byte size.
            //  Otherwise allocate the buffer for message data and read the
            //  message data into it.
            byte first = tmpbuf.get();
            if (first == 0xff) {
                tmpbuf.clear();
                next_step (tmpbuf, 8, eight_byte_size_ready);
            } else {

                //  There has to be at least one byte (the flags) in the message).
                if (first == 0) {
                    decoding_error ();
                    return false;
                }
                
                int size = (int) first;
                if (size < 0) {
                    size = (0xFF) & first;
                }

                //  in_progress is initialised at this point so in theory we should
                //  close it before calling zmq_msg_init_size, however, it's a 0-byte
                //  message and thus we can treat it as uninitialised...
                if (maxmsgsize >= 0 && (long) (size - 1) > maxmsgsize) {
                    decoding_error ();
                    return false;

                }
                else {
                    in_progress = new Msg(size-1);
                }

                tmpbuf.clear();
                next_step (tmpbuf, 1, flags_ready);
            }
            return true;

        }
        
        private boolean eight_byte_size_ready() {
            //  8-byte payload length is read. Allocate the buffer
            //  for message body and read the message data into it.
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
            next_step (tmpbuf, 1, flags_ready);
            
            return true;

        }
        
        private boolean flags_ready() {

            //  Store the flags from the wire into the message structure.
            
            byte first = tmpbuf.get();
            
            in_progress.set_flags (first);

            next_step (in_progress,
                message_ready);

            return true;

        }
        
        private boolean message_ready() {
            //  Message is completely read. Push it further and start reading
            //  new message. (in_progress is a 0-byte message after this point.)
            
            boolean rc = session.write (in_progress);
            if (!rc) {
                // full
                return false;
            }
            
            tmpbuf.clear();
            next_step (tmpbuf, 1, one_byte_size_ready);
            
            return true;
        }


        
        public boolean stalled ()
        {
            return state() == message_ready;
        }

    }
}
