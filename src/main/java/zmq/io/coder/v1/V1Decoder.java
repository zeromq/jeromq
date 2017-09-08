package zmq.io.coder.v1;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.ZError;
import zmq.io.coder.Decoder;
import zmq.msg.MsgAllocator;
import zmq.util.Errno;
import zmq.util.Wire;

public class V1Decoder extends Decoder
{
    private final ByteBuffer tmpbuf;

    public V1Decoder(Errno errno, int bufsize, long maxmsgsize, MsgAllocator allocator)
    {
        super(errno, bufsize, maxmsgsize, allocator);

        tmpbuf = ByteBuffer.allocate(8);
        tmpbuf.limit(1);

        //  At the beginning, read one byte and go to ONE_BYTE_SIZE_READY state.
        nextStep(tmpbuf, oneByteSizeReady);
    }

    @Override
    protected Step.Result oneByteSizeReady()
    {
        //  First byte of size is read. If it is 0xff read 8-byte size.
        //  Otherwise allocate the buffer for message data and read the
        //  message data into it.
        int size = tmpbuf.get(0) & 0xff;
        if (size == 0xff) {
            tmpbuf.position(0);
            tmpbuf.limit(8);
            nextStep(tmpbuf, eightByteSizeReady);
        }
        else {
            //  There has to be at least one byte (the flags) in the message).
            if (size <= 0) {
                errno(ZError.EPROTO);
                return Step.Result.ERROR;
            }
            tmpbuf.position(0);
            tmpbuf.limit(1);
            Step.Result rc = sizeReady(size - 1);
            if (rc != Step.Result.ERROR) {
                nextStep(tmpbuf, flagsReady);
            }

            return rc;
        }
        return Step.Result.MORE_DATA;
    }

    @Override
    protected Step.Result eightByteSizeReady()
    {
        //  8-byte payload length is read. Allocate the buffer
        //  for message body and read the message data into it.
        tmpbuf.position(0);
        tmpbuf.limit(8);
        final long payloadLength = Wire.getUInt64(tmpbuf, 0);

        if (payloadLength <= 0) {
            errno(ZError.EPROTO);
            return Step.Result.ERROR;
        }
        tmpbuf.limit(1);
        Step.Result rc = sizeReady(payloadLength - 1);
        if (rc != Step.Result.ERROR) {
            nextStep(tmpbuf, flagsReady);
        }
        return rc;
    }

    @Override
    protected Step.Result flagsReady()
    {
        //  Store the flags from the wire into the message structure.
        int first = tmpbuf.get(0) & 0xff;
        if ((first & V1Protocol.MORE_FLAG) > 0) {
            inProgress.setFlags(Msg.MORE);
        }

        nextStep(inProgress, messageReady);

        return Step.Result.MORE_DATA;
    }

    @Override
    protected Step.Result messageReady()
    {
        //  Message is completely read. Push it further and start reading
        //  new message. (inProgress is a 0-byte message after this point.)

        tmpbuf.position(0);
        tmpbuf.limit(1);
        nextStep(tmpbuf, oneByteSizeReady);

        return Step.Result.DECODED;
    }
}
