package zmq;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public abstract class EncoderBase implements IEncoder
{
    //  Where to get the data to write from.
    private ByteBuffer writeBuf;
    private FileChannel writeChannel;
    private int writePos;

    //  Next step. If set to -1, it means that associated data stream
    //  is dead.
    private int next;

    //  If true, first byte of the message is being written.
    @SuppressWarnings("unused")
    private boolean beginning;

    //  How much data to write before next step should be executed.
    private int toWrite;

    //  The buffer for encoded data.
    private ByteBuffer buffer;

    private int bufferSize;

    private boolean error;

    protected EncoderBase(int bufferSize)
    {
        this.bufferSize = bufferSize;
        buffer = ByteBuffer.allocateDirect(bufferSize);
        error = false;
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data_
    //  points to NULL) decoder object will provide buffer of its own.

    @Override
    public Transfer getData(ByteBuffer buffer)
    {
        if (buffer == null) {
            buffer = this.buffer;
        }

        buffer.clear();

        while (buffer.hasRemaining()) {
            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (toWrite == 0) {
                //  If we are to encode the beginning of a new message,
                //  adjust the message offset.

                if (!next()) {
                    break;
                }
            }

            //  If there is file channel to send,
            //  send current buffer and the channel together

            if (writeChannel != null) {
                buffer.flip();
                Transfer t = new Transfer.FileChannelTransfer(buffer, writeChannel,
                                                    (long) writePos, (long) toWrite);
                writePos = 0;
                toWrite = 0;

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
            if (this.buffer.position() == 0 && toWrite >= bufferSize) {
                Transfer t = new Transfer.ByteBufferTransfer(writeBuf);
                writePos = 0;
                toWrite = 0;

                return t;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int remaining = buffer.remaining();
            if (toWrite <= remaining) {
                buffer.put(writeBuf);
                writePos = 0;
                toWrite = 0;
            }
            else {
                writeBuf.limit(writePos + remaining);
                buffer.put(writeBuf);
                writePos += remaining;
                toWrite -= remaining;
                writeBuf.limit(writePos + toWrite);
            }
        }

        buffer.flip();
        return new Transfer.ByteBufferTransfer(buffer);
    }

    @Override
    public boolean hasData()
    {
        return toWrite > 0;
    }

    protected int state()
    {
        return next;
    }

    protected void state(int state)
    {
        next = state;
    }

    protected void encodingError()
    {
        error = true;
    }

    public final boolean isError()
    {
        return error;
    }

    protected abstract boolean next();

    protected void nextStep(Msg msg, int state, boolean beginning)
    {
        if (msg == null) {
            nextStep(null, 0, state, beginning);
        }
        else {
            nextStep(msg.buf(), state, beginning);
        }
    }

    protected void nextStep(byte[] buf, int toWrite,
                            int next, boolean beginning)
    {
        if (buf != null) {
            writeBuf = ByteBuffer.wrap(buf);
            writeBuf.limit(toWrite);
        }
        else {
            writeBuf = null;
        }
        writeChannel = null;
        writePos = 0;
        this.toWrite = toWrite;
        this.next = next;
        this.beginning = beginning;
    }

    protected void nextStep(ByteBuffer buf,
          int next, boolean beginning)
    {
       writeBuf = buf;
       writeChannel = null;
       writePos = buf.position();
       this.toWrite = buf.remaining();
       this.next = next;
       this.beginning = beginning;
    }

    protected void nextStep(FileChannel ch, long pos, long toWrite,
                            int next, boolean beginning)
    {
        writeBuf = null;
        writeChannel = ch;
        writePos = (int) pos;
        this.toWrite = (int) toWrite;
        this.next = next;
        this.beginning = beginning;
    }
}
