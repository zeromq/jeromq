package zmq.io.coder;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.util.Errno;
import zmq.util.ValueReference;

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
    private ByteBuffer readPos;

    // TODO V4 remove zeroCopy boolean
    private boolean zeroCopy;
    //  How much data to read before taking next step.
    private int toRead;

    //  The buffer for data to decode.
    private int bufsize;

    private ByteBuffer buf;

    private Step next;

    private final Errno errno;

    public DecoderBase(Errno errno, int bufsize)
    {
        next = null;
        readPos = null;
        toRead = 0;
        this.bufsize = bufsize;
        assert (bufsize > 0);
        buf = ByteBuffer.allocateDirect(bufsize);
        this.errno = errno;
    }

    //  Returns a buffer to be filled with binary data.
    @Override
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
        if (toRead >= bufsize) {
            zeroCopy = true;
            return readPos.duplicate();
        }
        else {
            zeroCopy = false;
            buf.clear();
            return buf;
        }
    }

    //  Processes the data in the buffer previously allocated using
    //  get_buffer function. size_ argument specifies number of bytes
    //  actually filled into the buffer. Function returns number of
    //  bytes actually processed.
    @Override
    public Step.Result decode(ByteBuffer data, int size, ValueReference<Integer> processed)
    {
        processed.set(0);

        //  In case of zero-copy simply adjust the pointers, no copying
        //  is required. Also, run the state machine in case all the data
        //  were processed.
        if (zeroCopy) {
            assert (size <= toRead);
            readPos.position(readPos.position() + size);
            toRead -= size;
            processed.set(size);

            while (readPos.remaining() == 0) {
                Step.Result result = next.apply();
                if (result != Step.Result.MORE_DATA) {
                    return result;
                }
            }
            return Step.Result.MORE_DATA;
        }

        while (processed.get() < size) {
            //  Copy the data from buffer to the message.
            int toCopy = Math.min(toRead, size - processed.get());
            int limit = buf.limit();
            buf.limit(buf.position() + toCopy);
            readPos.put(buf);
            buf.limit(limit);
            toRead -= toCopy;
            processed.set(processed.get() + toCopy);

            //  Try to get more space in the message to fill in.
            //  If none is available, return.
            while (readPos.remaining() == 0) {
                Step.Result result = next.apply();
                if (result != Step.Result.MORE_DATA) {
                    return result;
                }
            }
        }

        return Step.Result.MORE_DATA;
    }

    protected void nextStep(Msg msg, Step next)
    {
        nextStep(msg.buf(), next);
    }

    @Deprecated
    protected void nextStep(byte[] buf, int toRead, Step next)
    {
        readPos = ByteBuffer.wrap(buf);
        readPos.limit(toRead);
        this.toRead = toRead;
        this.next = next;
    }

    protected void nextStep(ByteBuffer buf, Step next)
    {
        readPos = buf;
        this.toRead = buf.remaining();
        this.next = next;
    }

    protected void errno(int err)
    {
        this.errno.set(err);
    }

    public int errno()
    {
        return errno.get();
    }

    @Override
    public void destroy()
    {
    }
}
