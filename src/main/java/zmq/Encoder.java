package zmq;

import java.nio.ByteBuffer;

public class Encoder extends EncoderBase
{
    private static final int SIZE_READY = 0;
    private static final int MESSAGE_READY = 1;

    private Msg inProgress;
    private final byte[] tmpbuf;
    private final ByteBuffer tmpbufWrap;
    private IMsgSource msgSource;

    public Encoder(int bufsize)
    {
        super(bufsize);
        tmpbuf = new byte[10];
        tmpbufWrap = ByteBuffer.wrap(tmpbuf);
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
        nextStep(inProgress.buf(),
                MESSAGE_READY, !inProgress.hasMore());
        return true;
    }

    private boolean messageReady()
    {
        //  Destroy content of the old message.
        //inProgress.close ();

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

        //  Get the message size.
        int size = inProgress.size();

        //  Account for the 'flags' byte.
        size++;

        //  For messages less than 255 bytes long, write one byte of message size.
        //  For longer messages write 0xff escape character followed by 8-byte
        //  message size. In both cases 'flags' field follows.
        tmpbufWrap.position(0);
        if (size < 255) {
            tmpbufWrap.limit(2);
            tmpbuf[0] = (byte) size;
            tmpbuf[1] = (byte) (inProgress.flags() & Msg.MORE);
            nextStep(tmpbufWrap, SIZE_READY, false);
        }
        else {
            tmpbufWrap.limit(10);
            tmpbuf[0] = (byte) 0xff;
            tmpbufWrap.putLong(1, size);
            tmpbuf[9] = (byte) (inProgress.flags() & Msg.MORE);
            nextStep(tmpbufWrap, SIZE_READY, false);
        }

        return true;
    }
}
