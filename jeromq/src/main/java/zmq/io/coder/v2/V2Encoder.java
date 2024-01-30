package zmq.io.coder.v2;

import java.nio.ByteBuffer;

import zmq.io.coder.Encoder;
import zmq.util.Errno;
import zmq.util.Wire;

// Encoder for 0MQ framing protocol. Converts messages into data stream.
public class V2Encoder extends Encoder
{
    private final ByteBuffer tmpbufWrap;

    public V2Encoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
        tmpbufWrap = ByteBuffer.allocate(9);

        //  Write 0 bytes to the batch and go to messageReady state.
        initStep(messageReady, true);
    }

    @Override
    protected void messageReady()
    {
        //  Encode flags.

        byte protocolFlags = 0;
        if (inProgress.hasMore()) {
            protocolFlags |= V2Protocol.MORE_FLAG;
        }
        if (inProgress.size() > 255) {
            protocolFlags |= V2Protocol.LARGE_FLAG;
        }
        if (inProgress.isCommand()) {
            protocolFlags |= V2Protocol.COMMAND_FLAG;
        }

        //  Encode the message length. For messages less then 256 bytes,
        //  the length is encoded as 8-bit unsigned integer. For larger
        //  messages, 64-bit unsigned integer in network byte order is used.

        final int size = inProgress.size();
        tmpbufWrap.position(0);
        tmpbufWrap.put(protocolFlags);

        if (size > 255) {
            tmpbufWrap.limit(9);
            Wire.putUInt64(tmpbufWrap, size);
        }
        else {
            tmpbufWrap.limit(2);
            tmpbufWrap.put((byte) size);
        }
        nextStep(tmpbufWrap, tmpbufWrap.limit(), sizeReady, false);
    }

    @Override
    protected void sizeReady()
    {
        //  Write message body into the buffer.
        nextStep(inProgress.buf(), inProgress.size(), messageReady, true);
    }
}
