package zmq;

import java.nio.ByteBuffer;

// Encoder for 0MQ framing protocol. Converts messages into data stream.

public class V1Encoder extends EncoderBase
{
    private static final int SIZE_READY = 0;
    private static final int MESSAGE_READY = 1;

    private Msg inProgress;
    private final byte[] tmpbuf;
    private final ByteBuffer tmpbufWrap;
    private IMsgSource msgSource;

    public V1Encoder(int bufsize, IMsgSource session)
    {
        super(bufsize);
        tmpbuf = new byte[9];
        tmpbufWrap = ByteBuffer.wrap(tmpbuf);
        msgSource = session;

        //  Write 0 bytes to the batch and go to messageReady state.
        nextStep((byte[]) null, 0, MESSAGE_READY, true);
    }

    @Override
    public void setMsgSource(IMsgSource msgSource)
    {
        this.msgSource = msgSource;
    }

    @Override
    protected boolean next()
    {
        switch(state()) {
        case SIZE_READY:
            return sizeReady();
        case MESSAGE_READY:
            return messageReady();
        default:
            return false;
        }
    }

    private boolean sizeReady()
    {
        //  Write message body into the buffer.
        nextStep(inProgress.buf(), MESSAGE_READY, !inProgress.hasMore());
        return true;
    }

    private boolean messageReady()
    {
        //  Read new message. If there is none, return false.
        //  Note that new state is set only if write is successful. That way
        //  unsuccessful write will cause retry on the next state machine
        //  invocation.

        if (msgSource == null) {
            return false;
        }

        inProgress = msgSource.pullMsg();
        if (inProgress == null) {
            return false;
        }

        int protocolFlags = 0;
        if (inProgress.hasMore()) {
            protocolFlags |= V1Protocol.MORE_FLAG;
        }
        if (inProgress.size() > 255) {
            protocolFlags |= V1Protocol.LARGE_FLAG;
        }
        tmpbuf[0] = (byte) protocolFlags;

        //  Encode the message length. For messages less then 256 bytes,
        //  the length is encoded as 8-bit unsigned integer. For larger
        //  messages, 64-bit unsigned integer in network byte order is used.
        final int size = inProgress.size();
        tmpbufWrap.position(0);
        if (size > 255) {
            tmpbufWrap.limit(9);
            tmpbufWrap.putLong(1, size);
            nextStep(tmpbufWrap, SIZE_READY, false);
        }
        else {
            tmpbufWrap.limit(2);
            tmpbuf[1] = (byte) (size);
            nextStep(tmpbufWrap, SIZE_READY, false);
        }
        return true;
    }
}
