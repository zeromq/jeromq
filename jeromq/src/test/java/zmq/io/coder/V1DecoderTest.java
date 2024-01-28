package zmq.io.coder;

import java.nio.ByteBuffer;

import org.junit.Before;

import zmq.ZMQ;
import zmq.io.coder.v1.V1Decoder;
import zmq.msg.MsgAllocatorThreshold;
import zmq.util.Errno;
import zmq.util.Wire;

public class V1DecoderTest extends AbstractDecoderTest
{
    @Before
    public void setUp()
    {
        decoder = new V1Decoder(new Errno(), 64, 512, new MsgAllocatorThreshold());
    }

    // as if it read data from socket
    @Override
    int readShortMessage(ByteBuffer buf)
    {
        buf.put((byte) 6);
        buf.put((byte) 1); // flag
        buf.put("hello".getBytes(ZMQ.CHARSET));

        return buf.position();
    }

    // as if it read data from socket
    @Override
    int readLongMessage1(ByteBuffer buf)
    {
        buf.put((byte) 201);
        buf.put((byte) 3); // flag
        for (int i = 0; i < 6; i++) {
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        buf.put("01".getBytes(ZMQ.CHARSET));
        return buf.position();
    }

    @Override
    int readLongMessage2(ByteBuffer buf)
    {
        for (int i = 0; i < 13; i++) {
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        buf.put(buf.position() - 1, (byte) 'x');
        return buf.position();
    }

    @Override
    int readExtraLongMessage(ByteBuffer buf)
    {
        buf.put((byte) 0xff);
        Wire.putUInt64(buf, 331);
        buf.put((byte) 0); // flag
        for (int i = 0; i < 5; i++) {
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        buf.put("01".getBytes(ZMQ.CHARSET));
        return buf.position();
    }
}
