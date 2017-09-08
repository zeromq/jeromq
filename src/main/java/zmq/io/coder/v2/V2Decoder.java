package zmq.io.coder.v2;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.io.coder.Decoder;
import zmq.msg.MsgAllocator;
import zmq.util.Errno;
import zmq.util.Wire;

public class V2Decoder extends Decoder
{
    private final ByteBuffer tmpbuf;
    private int msgFlags;

    public V2Decoder(Errno errno, int bufsize, long maxmsgsize, MsgAllocator allocator)
    {
        super(errno, bufsize, maxmsgsize, allocator);

        tmpbuf = ByteBuffer.allocate(8);
        tmpbuf.limit(1);

        //  At the beginning, read one byte and go to ONE_BYTE_SIZE_READY state.
        nextStep(tmpbuf, flagsReady);
    }

    @Override
    protected Msg allocate(int size)
    {
        Msg msg = super.allocate(size);
        msg.setFlags(msgFlags);
        return msg;
    }

    @Override
    protected Step.Result oneByteSizeReady()
    {
        int size = tmpbuf.get(0) & 0xff;
        Step.Result rc = sizeReady(size);
        if (rc != Step.Result.ERROR) {
            nextStep(inProgress, messageReady);
        }
        return rc;
    }

    @Override
    protected Step.Result eightByteSizeReady()
    {
        //  The payload size is encoded as 64-bit unsigned integer.
        //  The most significant byte comes first.
        tmpbuf.position(0);
        tmpbuf.limit(8);
        final long size = Wire.getUInt64(tmpbuf, 0);

        Step.Result rc = sizeReady(size);
        if (rc != Step.Result.ERROR) {
            nextStep(inProgress, messageReady);
        }
        return rc;
    }

    @Override
    protected Step.Result flagsReady()
    {
        //  Store the flags from the wire into the message structure.
        this.msgFlags = 0;
        int first = tmpbuf.get(0) & 0xff;
        if ((first & V2Protocol.MORE_FLAG) > 0) {
            this.msgFlags |= Msg.MORE;
        }
        if ((first & V2Protocol.COMMAND_FLAG) > 0) {
            this.msgFlags |= Msg.COMMAND;
        }

        //  The payload length is either one or eight bytes,
        //  depending on whether the 'large' bit is set.
        tmpbuf.position(0);
        if ((first & V2Protocol.LARGE_FLAG) > 0) {
            tmpbuf.limit(8);
            nextStep(tmpbuf, eightByteSizeReady);
        }
        else {
            tmpbuf.limit(1);
            nextStep(tmpbuf, oneByteSizeReady);
        }

        return Step.Result.MORE_DATA;
    }

    @Override
    protected Step.Result messageReady()
    {
        //  Message is completely read. Signal this to the caller
        //  and prepare to decode next message.
        tmpbuf.position(0);
        tmpbuf.limit(1);
        nextStep(tmpbuf, flagsReady);

        return Step.Result.DECODED;
    }
}
