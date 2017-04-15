package zmq.io.coder;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.util.Errno;
import zmq.util.ValueReference;

public abstract class EncoderBase implements IEncoder
{
    //  Where to get the data to write from.
    private ByteBuffer writeBuf;

    //  Next step. If set to null, it means that associated data stream
    //  is dead.
    private Runnable next;

    //  If true, first byte of the message is being written.
    private boolean newMsgFlag;

    //  How much data to write before next step should be executed.
    private int toWrite;

    //  The buffer for encoded data.
    private final ByteBuffer buffer;

    private final int bufferSize;

    private boolean error;

    protected Msg inProgress;

    private final Errno errno;

    protected EncoderBase(Errno errno, int bufferSize)
    {
        this.errno = errno;
        this.bufferSize = bufferSize;
        buffer = ByteBuffer.allocateDirect(bufferSize);
        error = false;
    }

    //  Load a new message into encoder.
    @Override
    public final void loadMsg(Msg msg)
    {
        assert (inProgress == null);
        inProgress = msg;
        next();
    }

    //  The function returns a batch of binary data. The data
    //  are filled to a supplied buffer. If no buffer is supplied (data
    //  is NULL) encoder will provide buffer of its own.
    @Override
    public final int encode(ValueReference<ByteBuffer> data, int size)
    {
        int bufferSize = size;
        ByteBuffer buf = data.get();
        if (buf == null) {
            buf = this.buffer;
            bufferSize = this.bufferSize;
            buffer.clear();
        }

        if (inProgress == null) {
            return 0;
        }

        int pos = 0;

        buf.limit(buf.capacity());
        while (pos < bufferSize) {
            //  If there are no more data to return, run the state machine.
            //  If there are still no data, return what we already have
            //  in the buffer.
            if (toWrite == 0) {
                if (newMsgFlag) {
                    inProgress = null;
                    break;
                }
                next();
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
            if (pos == 0 && data.get() == null && toWrite >= bufferSize) {
                writeBuf.limit(writeBuf.capacity());
                data.set(writeBuf);
                pos = toWrite;
                writeBuf = null;
                toWrite = 0;
                return pos;
            }

            //  Copy data to the buffer. If the buffer is full, return.
            int toCopy = Math.min(toWrite, bufferSize - pos);
            int limit = writeBuf.limit();
            writeBuf.limit(Math.min(writeBuf.capacity(), writeBuf.position() + toCopy));
            int current = buf.position();
            buf.put(writeBuf);
            toCopy = buf.position() - current;
            writeBuf.limit(limit);
            pos += toCopy;
            toWrite -= toCopy;
        }

        data.set(buf);

        return pos;
    }

    protected void encodingError()
    {
        error = true;
    }

    public final boolean isError()
    {
        return error;
    }

    protected void next()
    {
        if (next != null) {
            next.run();
        }
    }

    protected void nextStep(Msg msg, Runnable state, boolean beginning)
    {
        if (msg == null) {
            nextStep((byte[]) null, 0, state, beginning);
        }
        else {
            nextStep(msg.buf(), state, beginning);
        }
    }

    //  This function should be called from derived class to write the data
    //  to the buffer and schedule next state machine action.
    private void nextStep(byte[] buf, int toWrite, Runnable next, boolean newMsgFlag)
    {
        if (buf != null) {
            writeBuf = ByteBuffer.wrap(buf);
            writeBuf.limit(toWrite);
        }
        else {
            writeBuf = null;
        }
        this.toWrite = toWrite;
        this.next = next;
        this.newMsgFlag = newMsgFlag;
    }

    protected void initStep(Runnable next, boolean newMsgFlag)
    {
        nextStep((byte[]) null, 0, next, newMsgFlag);
    }

    private void nextStep(ByteBuffer buf, Runnable next, boolean newMsgFlag)
    {
        nextStep(buf, buf.limit(), next, newMsgFlag);
    }

    protected void nextStep(ByteBuffer buf, int toWrite, Runnable next, boolean newMsgFlag)
    {
        buf.limit(toWrite);
        buf.position(toWrite);
        buf.flip();
        writeBuf = buf;
        this.toWrite = toWrite;
        this.next = next;
        this.newMsgFlag = newMsgFlag;
    }

    public int errno()
    {
        return errno.get();
    }

    public void errno(int err)
    {
        this.errno.set(err);
    }

    @Override
    public void destroy()
    {
    }
}
