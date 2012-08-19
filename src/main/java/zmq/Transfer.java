package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

public interface Transfer {

    public int transferTo(SocketChannel s) throws IOException;
    public int remaining();
    
    public static class ByteBufferTransfer implements Transfer {

        private ByteBuffer buf;
        
        public ByteBufferTransfer (ByteBuffer buf_) {
            buf = buf_;
        }
        
        @Override
        public int transferTo(SocketChannel s) throws IOException {
            return s.write(buf);
        }

        @Override
        public int remaining() {
            return buf.remaining();
        }
        
    }
    
    public static class FileChannelTransfer implements Transfer {
        
        private FileChannel ch;
        
        public FileChannelTransfer (FileChannel ch_) {
            ch = ch_;
        }
        
        @Override
        public int transferTo(SocketChannel s) throws IOException {
            return (int) ch.transferTo(0L, 0L, s);
        }

        @Override
        public int remaining() {
            return 0;
        }
    }

    
}
