package zmq.io.coder;

import zmq.Msg;
import zmq.ZError;
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

    private final class OneByteSizeReady implements Step
    {
        @Override
        public Step.Result apply()
        {
            return oneByteSizeReady();
        }
    }

    protected final long maxmsgsize;

    // the message decoded so far
    protected Msg inProgress;

    protected final Step oneByteSizeReady   = new OneByteSizeReady();
    protected final Step eightByteSizeReady = new EightByteSizeReady();
    protected final Step flagsReady         = new FlagsReady();
    protected final Step messageReady       = new MessageReady();

    private final MsgAllocator allocator;

    public Decoder(Errno errno, int bufsize, long maxmsgsize, MsgAllocator allocator)
    {
        super(errno, bufsize);
        this.maxmsgsize = maxmsgsize;
        this.allocator = allocator;
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
        inProgress = allocate((int) size);

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
