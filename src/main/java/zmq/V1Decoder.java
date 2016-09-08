package zmq;

import java.nio.ByteBuffer;

public class V1Decoder extends DecoderBase
{
    private static final int ONE_BYTE_SIZE_READY = 0;
    private static final int EIGHT_BYTE_SIZE_READY = 1;
    private static final int FLAGS_READY = 2;
    private static final int MESSAGE_READY = 3;

    private final byte[] tmpbuf;
    private final ByteBuffer tmpbufWrap;
    private Msg inProgress;
    private IMsgSink msgSink;
    private final long maxmsgsize;
    private int msgFlags;

    public V1Decoder(int bufsize, long maxmsgsize, IMsgSink session)
    {
        super(bufsize);

        this.maxmsgsize = maxmsgsize;
        msgSink = session;

        tmpbuf = new byte[8];
        tmpbufWrap = ByteBuffer.wrap(tmpbuf);
        tmpbufWrap.limit(1);

        //  At the beginning, read one byte and go to ONE_BYTE_SIZE_READY state.
        nextStep(tmpbufWrap, FLAGS_READY);
    }

    //  Set the receiver of decoded messages.
    @Override
    public void setMsgSink(IMsgSink msgSink)
    {
        this.msgSink = msgSink;
    }

    @Override
    protected boolean next()
    {
        switch(state()) {
        case ONE_BYTE_SIZE_READY:
            return oneByteSizeReady();
        case EIGHT_BYTE_SIZE_READY:
            return eightByteSizeReady();
        case FLAGS_READY:
            return flagsReady();
        case MESSAGE_READY:
            return messageReady();
        default:
            return false;
        }
    }

    private boolean oneByteSizeReady()
    {
        int size = tmpbuf[0];
        if (size < 0) {
            size = (0xff) & size;
        }

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0) {
            if (size > maxmsgsize) {
                decodingError();
                return false;
            }
        }

        //  inProgress is initialised at this point so in theory we should
        //  close it before calling msgInitWithSize, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised...
        inProgress = getMsgAllocator().allocate(size);

        inProgress.setFlags(msgFlags);
        nextStep(inProgress,
                MESSAGE_READY);

        return true;
    }

    private boolean eightByteSizeReady()
    {
        //  The payload size is encoded as 64-bit unsigned integer.
        //  The most significant byte comes first.
        tmpbufWrap.position(0);
        tmpbufWrap.limit(8);
        final long msgSize = tmpbufWrap.getLong(0);

        //  Message size must not exceed the maximum allowed size.
        if (maxmsgsize >= 0) {
            if (msgSize > maxmsgsize) {
                decodingError();
                return false;
            }
        }

        //  Message size must fit within range of size_t data type.
        if (msgSize > Integer.MAX_VALUE) {
            decodingError();
            return false;
        }

        //  inProgress is initialised at this point so in theory we should
        //  close it before calling init_size, however, it's a 0-byte
        //  message and thus we can treat it as uninitialised.
        inProgress = getMsgAllocator().allocate((int) msgSize);

        inProgress.setFlags(msgFlags);
        nextStep(inProgress,
                MESSAGE_READY);

        return true;
    }

    private boolean flagsReady()
    {
        //  Store the flags from the wire into the message structure.
        msgFlags = 0;
        int first = tmpbuf[0];
        if ((first & V1Protocol.MORE_FLAG) > 0) {
            msgFlags |= Msg.MORE;
        }

        //  The payload length is either one or eight bytes,
        //  depending on whether the 'large' bit is set.
        tmpbufWrap.position(0);
        if ((first & V1Protocol.LARGE_FLAG) > 0) {
            tmpbufWrap.limit(8);
            nextStep(tmpbufWrap, EIGHT_BYTE_SIZE_READY);
        }
        else {
            tmpbufWrap.limit(1);
            nextStep(tmpbufWrap, ONE_BYTE_SIZE_READY);
        }

        return true;
    }

    private boolean messageReady()
    {
        //  Message is completely read. Push it further and start reading
        //  new message. (inProgress is a 0-byte message after this point.)

        if (msgSink == null) {
            return false;
        }

        int rc = msgSink.pushMsg(inProgress);
        if (rc != 0) {
            if (rc != ZError.EAGAIN) {
                decodingError();
            }

            return false;
        }

        tmpbufWrap.position(0);
        tmpbufWrap.limit(1);
        nextStep(tmpbufWrap, FLAGS_READY);

        return true;
    }
}
