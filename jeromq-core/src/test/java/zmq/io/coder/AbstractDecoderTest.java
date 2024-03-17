package zmq.io.coder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

import zmq.Msg;
import zmq.ZMQ;
import zmq.io.coder.IDecoder.Step;
import zmq.util.ValueReference;

public abstract class AbstractDecoderTest
{
    DecoderBase decoder;

    // as if it read data from socket
    abstract int readShortMessage(ByteBuffer buf);

    // as if it read data from socket
    abstract int readLongMessage1(ByteBuffer buf);

    abstract int readLongMessage2(ByteBuffer buf);

    abstract int readExtraLongMessage(ByteBuffer buf);

    @Test
    public void testReader()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readShortMessage(in);

        assertThat(insize, is(7));
        in.flip();
        ValueReference<Integer> process = new ValueReference<>(0);
        decoder.decode(in, insize, process);
        assertThat(process.get(), is(7));
        Msg msg = decoder.msg();
        assertThat(msg, notNullValue());
        assertThat(msg.flags(), is(1));

    }

    @Test
    public void testReaderLong()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readLongMessage1(in);

        assertThat(insize, is(64));
        in.flip();
        ValueReference<Integer> process = new ValueReference<>(0);
        decoder.decode(in, insize, process);
        assertThat(process.get(), is(64));

        in = decoder.getBuffer();
        assertThat(in.capacity(), is(200));
        assertThat(in.position(), is(62));

        in.put("23456789".getBytes(ZMQ.CHARSET));
        insize = readLongMessage2(in);

        assertThat(insize, is(200));
        decoder.decode(in, 138, process);
        assertThat(process.get(), is(138));
        assertThat(in.array()[199], is((byte) 'x'));

        Msg msg = decoder.msg();
        assertThat(msg, notNullValue());
        byte last = msg.data()[199];
        assertThat(last, is((byte) 'x'));
        assertThat(msg.size(), is(200));
        assertThat(msg.flags(), is(1));
    }

    @Test
    public void testReaderExtraLong()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readExtraLongMessage(in);

        //        assertThat(insize, is(62));
        in.flip();
        ValueReference<Integer> process = new ValueReference<>(0);
        decoder.decode(in, insize, process);
        assertThat(process.get(), is(insize));

        in = decoder.getBuffer();
        assertThat(in.capacity(), is(330));
        assertThat(in.position(), is(52));

        in.put("23456789".getBytes(ZMQ.CHARSET));
        in.put("0123456789".getBytes(ZMQ.CHARSET));
        insize = readLongMessage2(in);

        assertThat(insize, is(200));
        insize = readLongMessage2(in);

        assertThat(insize, is(330));

        decoder.decode(in, 278, process);
        assertThat(process.get(), is(278));
        assertThat(in.array()[329], is((byte) 'x'));

        Msg msg = decoder.msg();
        assertThat(msg, notNullValue());
        byte last = msg.data()[329];
        assertThat(last, is((byte) 'x'));
        assertThat(msg.size(), is(330));
    }

    @Test
    public void testReaderMultipleMsg()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readShortMessage(in);
        assertThat(insize, is(7));
        readShortMessage(in);

        in.flip();
        ValueReference<Integer> processed = new ValueReference<>(0);
        decoder.decode(in, 14, processed);
        assertThat(processed.get(), is(7));
        assertThat(in.position(), is(7));

        assertThat(decoder.msg(), notNullValue());

        Step.Result result = decoder.decode(in, 6, processed);
        assertThat(processed.get(), is(6));
        assertThat(in.position(), is(13));
        assertThat(result, is(Step.Result.MORE_DATA));

        result = decoder.decode(in, 10, processed);
        assertThat(processed.get(), is(1));
        assertThat(in.position(), is(14));
        assertThat(result, is(Step.Result.DECODED));
        assertThat(decoder.msg(), notNullValue());
    }
}
