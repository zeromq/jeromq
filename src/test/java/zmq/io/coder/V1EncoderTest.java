package zmq.io.coder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

import zmq.Msg;
import zmq.ZMQ;
import zmq.io.coder.v1.V1Encoder;
import zmq.util.Errno;
import zmq.util.ValueReference;

public class V1EncoderTest
{
    private final EncoderBase encoder = new V1Encoder(new Errno(), 64);

    // as if it read data from socket
    private Msg readShortMessage()
    {
        return new Msg("hello".getBytes(ZMQ.CHARSET));
    }

    // as if it read data from socket
    private Msg readLongMessage1()
    {
        Msg msg = new Msg(200);
        for (int i = 0; i < 20; i++) {
            msg.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        return msg;
    }

    @Test
    public void testReader()
    {
        Msg msg = readShortMessage();
        encoder.loadMsg(msg);
        ValueReference<ByteBuffer> ref = new ValueReference<>();
        int outsize = encoder.encode(ref, 0);
        ByteBuffer out = ref.get();

        assertThat(out, notNullValue());
        assertThat(outsize, is(7));
        assertThat(out.position(), is(7));
    }

    @Test
    public void testReaderLong()
    {
        Msg msg = readLongMessage1();
        ValueReference<ByteBuffer> ref = new ValueReference<>();
        int outsize = encoder.encode(ref, 0);
        assertThat(outsize, is(0));
        ByteBuffer out = ref.get();
        assertThat(out, nullValue());

        encoder.loadMsg(msg);
        outsize = encoder.encode(ref, 64);
        assertThat(outsize, is(64));
        out = ref.get();
        int position = out.position();
        int limit = out.limit();
        assertThat(limit, is(64));
        assertThat(position, is(64));

        ref.set(null);
        outsize = encoder.encode(ref, 64);
        assertThat(outsize, is(138));
        out = ref.get();
        position = out.position();
        limit = out.limit();
        assertThat(position, is(62));
        assertThat(limit, is(200));
    }
}
