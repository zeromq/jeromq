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

public abstract class DecoderBase implements IDecoder
{
    //  Where to store the read data.
    private ByteBuffer readBuf;
    private MsgAllocator msgAllocator = new MsgAllocatorHeap();

    //  The buffer for data to decode.
    private int bufsize;
    private ByteBuffer buf;

    private int state;

    boolean zeroCopy;

    public DecoderBase(int bufsize)
    {
        state = -1;
        this.bufsize = bufsize;
        if (bufsize > 0) {
            buf = ByteBuffer.allocateDirect(bufsize);
        }
        readBuf = null;
        zeroCopy = false;
    }

    //  Returns a buffer to be filled with binary data.
    public ByteBuffer getBuffer()
    {
        //  If we are expected to read large message, we'll opt for zero-
        //  copy, i.e. we'll ask caller to fill the data directly to the
        //  message. Note that subsequent read(s) are non-blocking, thus
        //  each single read reads at most SO_RCVBUF bytes at once not
        //  depending on how large is the chunk returned from here.
        //  As a consequence, large messages being received won't block
        //  other engines running in the same I/O thread for excessive
        //  amounts of time.

        if (readBuf.remaining() >= bufsize) {
            zeroCopy = true;
            return readBuf.duplicate();
        }
        else {
            zeroCopy = false;
            buf.clear();
            return buf;
        }
    }

    //  Processes the data in the buffer previously allocated using
    //  get_buffer function. size_ argument specifies nemuber of bytes
    //  actually filled into the buffer. Function returns number of
    //  bytes actually processed.
    public int processBuffer(ByteBuffer buf, int size)
    {
        //  Check if we had an error in previous attempt.
        if (state() < 0) {
            return -1;
        }

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (zeroCopy) {
            readBuf.position(readBuf.position() + size);

            while (readBuf.remaining() == 0) {
                if (!next()) {
                    if (state() < 0) {
                        return -1;
                    }
                    return size;
                }
            }
            return size;
        }

        int pos = 0;
        while (true) {
            //  Try to get more space in the message to fill in.
            //  If none is available, return.
            while (readBuf.remaining() == 0) {
                if (!next()) {
                    if (state() < 0) {
                        return -1;
                    }

                    return pos;
                }
            }

            //  If there are no more data in the buffer, return.
            if (pos == size) {
                return pos;
            }

            //  Copy the data from buffer to the message.
            int toCopy = Math.min(readBuf.remaining(), size - pos);
            int limit = buf.limit();
            buf.limit(buf.position() + toCopy);
            readBuf.put(buf);
            buf.limit(limit);
            pos += toCopy;
        }
    }

    protected void nextStep(Msg msg, int state)
    {
        nextStep(msg.buf(), state);
    }

    protected void nextStep(byte[] buf, int toRead, int state)
    {
        readBuf = ByteBuffer.wrap(buf);
        readBuf.limit(toRead);
        this.state = state;
    }

    protected void nextStep(ByteBuffer buf, int state)
    {
        readBuf = buf;
        this.state = state;
    }

    protected int state()
    {
        return state;
    }

    protected void state(int state)
    {
        this.state = state;
    }

    protected void decodingError()
    {
        state(-1);
    }

    //  Returns true if the decoder has been fed all required data
    //  but cannot proceed with the next decoding step.
    //  False is returned if the decoder has encountered an error.
    @Override
    public boolean stalled()
    {
        //  Check whether there was decoding error.
        if (!next()) {
            return false;
        }

        while (readBuf.remaining() == 0) {
            if (!next()) {
                return next();
            }
        }
        return false;
    }

    public MsgAllocator getMsgAllocator()
    {
       return msgAllocator;
    }

    public void setMsgAllocator(MsgAllocator msgAllocator)
    {
       this.msgAllocator = msgAllocator;
    }

    protected abstract boolean next();
}
