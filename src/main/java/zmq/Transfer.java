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
package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public interface Transfer {

    public int transferTo (WritableByteChannel s) throws IOException;
    public int remaining ();
    
    public static class ByteBufferTransfer implements Transfer {

        private ByteBuffer buf;
        
        public ByteBufferTransfer (ByteBuffer buf_) {
            buf = buf_;
        }
        
        @Override
        public final int transferTo (WritableByteChannel s) throws IOException {
            return s.write (buf);
        }

        @Override
        public final int remaining () {
            return buf.remaining ();
        }
        
    }
    
    public static class FileChannelTransfer implements Transfer {
        private Transfer parent;
        private FileChannel ch;
        private long position;
        private long count;
        private int remaining;
        
        public FileChannelTransfer (ByteBuffer buf_, FileChannel ch_, long position_, long count_) {
            parent = new ByteBufferTransfer (buf_);
            ch = ch_;
            position = position_;
            count = count_;
            remaining = parent.remaining () + (int) count;
        }
        
        @Override
        public final int transferTo(WritableByteChannel s) throws IOException {
            int sent = 0;
            if (parent.remaining () > 0)
                sent = parent.transferTo (s);
            
            if (parent.remaining () == 0) {
                long fileSent = ch.transferTo (position, count, s);
                position += fileSent;
                count -= fileSent;
                sent += fileSent;
            }
            
            remaining -= sent;
            
            if (remaining == 0)
                ch.close ();
            
            return sent;
        }

        @Override
        public final int remaining () {
            return remaining;
        }
    }

    
}
