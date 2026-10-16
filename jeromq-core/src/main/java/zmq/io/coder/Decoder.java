package zmq.io.coder;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.ZError;
import zmq.ZMQ;
import zmq.msg.MsgAllocator;
import zmq.util.Errno;

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

public abstract class Decoder extends DecoderBase
{
    private final class MessageReady implements Step
    {
        @Override
        public Step.Result apply()
        {
            return messageReady();
        }
    }

    private final class FlagsReady implements Step
    {
        @Override
        public Step.Result apply()
        {
            return flagsReady();
        }
    }

    private final class EightByteSizeReady implements Step
    {
        @Override
        public Step.Result apply()
        {
            return eightByteSizeReady();
        }
    }

    private final class GrowMessage implements Step
    {
        @Override
        public Step.Result apply()
        {
            return growMessage();
        }
    }

    private final class OneByteSizeReady implements Step
    {
        @Override
        public Step.Result apply()
        {
            return oneByteSizeReady();
        }
    }

    protected final long maxmsgsize;

    //  If positive, messages larger than this aren't allocated upfront, but
    //  grown as their content comes in. A peer can't make us allocate more
    //  than twice the amount of data it actually sent this way, whatever
    //  the size it announces in a frame header.
    //  If 0 or negative, messages are allocated with their announced size
    //  right away.
    private final int initialAllocationLimit;

    // the message decoded so far
    protected Msg inProgress;

    //  Final size of inProgress, if it's still being grown
    private int expectedSize;

    //  Step to run once inProgress is fully read, if it's still being grown
    private Step afterGrowth;

    private final Step growMessage = new GrowMessage();

    protected final Step oneByteSizeReady   = new OneByteSizeReady();
    protected final Step eightByteSizeReady = new EightByteSizeReady();
    protected final Step flagsReady         = new FlagsReady();
    protected final Step messageReady       = new MessageReady();

    private final MsgAllocator allocator;

    public Decoder(Errno errno, int bufsize, long maxmsgsize, MsgAllocator allocator)
    {
        this(errno, bufsize, maxmsgsize, allocator, ZMQ.DEFAULT_MSG_INITIAL_ALLOCATION_LIMIT);
    }

    public Decoder(Errno errno, int bufsize, long maxmsgsize, MsgAllocator allocator, int initialAllocationLimit)
    {
        super(errno, bufsize);
        this.maxmsgsize = maxmsgsize;
        this.allocator = allocator;
        this.initialAllocationLimit = initialAllocationLimit;
    }

    protected final Step.Result sizeReady(final long size)
    {
        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0) {
            if (size > maxmsgsize) {
                errno(ZError.EMSGSIZE);
                return Step.Result.ERROR;
            }
        }

        //  Message size must fit within range of size_t data type.
        if (size > Integer.MAX_VALUE) {
            errno(ZError.EMSGSIZE);
            return Step.Result.ERROR;
        }

        //  inProgress is initialized at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialized.
        expectedSize = (int) size;
        if (initialAllocationLimit > 0) {
            inProgress = allocate(Math.min(expectedSize, initialAllocationLimit));
        }
        else {
            inProgress = allocate(expectedSize);
        }

        return Step.Result.MORE_DATA;
    }

    @Override
    protected void nextStep(Msg msg, Step next)
    {
        if (msg == inProgress && msg.size() < expectedSize) {
            //  The message isn't fully allocated yet, grow it once it's full
            afterGrowth = next;
            super.nextStep(msg, growMessage);
        }
        else {
            super.nextStep(msg, next);
        }
    }

    //  Called when the partially allocated inProgress is full:
    //  allocates a larger message, and copies the data read so far in it.
    private Step.Result growMessage()
    {
        Msg previous = inProgress;
        int newSize = (int) Math.min(2L * previous.size(), expectedSize);

        Msg grown = allocate(newSize);
        grown.setFlags(previous.flags());

        ByteBuffer readPos = grown.buf();
        readPos.put(previous.buf());
        inProgress = grown;

        if (newSize < expectedSize) {
            super.nextStep(readPos, growMessage);
        }
        else {
            Step next = afterGrowth;
            afterGrowth = null;
            super.nextStep(readPos, next);
        }
        return Step.Result.MORE_DATA;
    }

    protected Msg allocate(final int size)
    {
        return allocator.allocate(size);
    }

    protected Step.Result oneByteSizeReady()
    {
        throw new UnsupportedOperationException("Have you forgot to implement oneByteSizeReady ?");
    }

    protected Step.Result eightByteSizeReady()
    {
        throw new UnsupportedOperationException("Have you forgot to implement eightByteSizeReady ?");
    }

    protected Step.Result flagsReady()
    {
        throw new UnsupportedOperationException("Have you forgot to implement flagsReady ?");
    }

    protected Step.Result messageReady()
    {
        throw new UnsupportedOperationException("Have you forgot to implement messageReady ?");
    }

    protected Step.Result messageIncomplete()
    {
        return Step.Result.MORE_DATA;
    }

    @Override
    public Msg msg()
    {
        return inProgress;
    }
}
