package zmq.io.coder.v1;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.io.coder.Encoder;
import zmq.util.Errno;
import zmq.util.Wire;

// Encoder for 0MQ framing protocol. Converts messages into data stream.
public class V1Encoder extends Encoder
{
    private final ByteBuffer tmpbufWrap;

    public V1Encoder(Errno errno, int bufsize)
    {
        super(errno, bufsize);
        tmpbufWrap = ByteBuffer.allocate(10);

        //  Write 0 bytes to the batch and go to messageReady state.
        initStep(messageReady, true);
    }

    @Override
    protected void sizeReady()
    {
        //  Write message body into the buffer.
        nextStep(inProgress.buf(), inProgress.size(), messageReady, true);
    }

    @Override
    protected void messageReady()
    {
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
            tmpbufWrap.put((byte) size);
        }
        else {
            tmpbufWrap.limit(10);
            tmpbufWrap.put((byte) 0xff);
            Wire.putUInt64(tmpbufWrap, size);
        }

        tmpbufWrap.put((byte) (inProgress.flags() & Msg.MORE));
        nextStep(tmpbufWrap, tmpbufWrap.limit(), sizeReady, false);
    }
}
