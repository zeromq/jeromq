/*
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2010 iMatix Corporation
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public interface Transfer {

    public int transferTo(WritableByteChannel s) throws IOException;
    public int remaining();
    
    public static class ByteBufferTransfer implements Transfer {

        private ByteBuffer buf;
        
        public ByteBufferTransfer (ByteBuffer buf_) {
            buf = buf_;
        }
        
        @Override
        public int transferTo(WritableByteChannel s) throws IOException {
            return s.write(buf);
        }

        @Override
        public int remaining() {
            return buf.remaining();
        }
        
    }
    
    public static class FileChannelTransfer implements Transfer {
        //TODO
        private FileChannel ch;
        
        public FileChannelTransfer (FileChannel ch_) {
            ch = ch_;
        }
        
        @Override
        public int transferTo(WritableByteChannel s) throws IOException {
            return (int) ch.transferTo(0L, 0L, s);
        }

        @Override
        public int remaining() {
            return 0;
        }
    }

    
}
