package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public interface Transfer
{
    public int transferTo(WritableByteChannel s) throws IOException;
    public int remaining();

    public static class ByteBufferTransfer implements Transfer
    {
        private ByteBuffer buf;

        public ByteBufferTransfer(ByteBuffer buf)
        {
            this.buf = buf;
        }

        @Override
        public final int transferTo(WritableByteChannel s) throws IOException
        {
            return s.write(buf);
        }

        @Override
        public final int remaining()
        {
            return buf.remaining();
        }
    }

    public static class FileChannelTransfer implements Transfer
    {
        private Transfer parent;
        private FileChannel channel;
        private long position;
        private long count;
        private int remaining;

        public FileChannelTransfer(ByteBuffer buf, FileChannel channel, long position, long count)
        {
            parent = new ByteBufferTransfer(buf);
            this.channel = channel;
            this.position = position;
            this.count = count;
            remaining = parent.remaining() + (int) this.count;
        }

        @Override
        public final int transferTo(WritableByteChannel s) throws IOException
        {
            int sent = 0;
            if (parent.remaining() > 0) {
                sent = parent.transferTo(s);
            }

            if (parent.remaining() == 0) {
                long fileSent = channel.transferTo(position, count, s);
                position += fileSent;
                count -= fileSent;
                sent += fileSent;
            }

            remaining -= sent;

            if (remaining == 0) {
                channel.close();
            }

            return sent;
        }

        @Override
        public final int remaining()
        {
            return remaining;
        }
    }
}
