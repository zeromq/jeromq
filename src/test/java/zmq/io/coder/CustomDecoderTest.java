package zmq.io.coder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.io.coder.IDecoder.Step;
import zmq.msg.MsgAllocatorThreshold;
import zmq.util.Errno;
import zmq.util.ValueReference;

public class CustomDecoderTest
{
    static class CustomDecoder extends Decoder
    {
        private final Step readHeader = new Step()
                                      {
                                          @Override
                                          public Step.Result apply()
                                          {
                                              return readHeader();
                                          }
                                      };
        private final Step readBody   = new Step()
                                      {
                                          @Override
                                          public Step.Result apply()
                                          {
                                              return readBody();
                                          }
                                      };

        byte[] header = new byte[10];
        Msg    msg;
        int    size   = -1;

        public CustomDecoder(int bufsize, long maxmsgsize)
        {
            super(new Errno(), bufsize, maxmsgsize, new MsgAllocatorThreshold());
            nextStep(header, 10, readHeader);
        }

        private Step.Result readHeader()
        {
            assertThat(new String(header, 0, 6, ZMQ.CHARSET), is("HEADER"));
            ByteBuffer b = ByteBuffer.wrap(header, 6, 4);
            size = b.getInt();

            msg = allocate(size);
            nextStep(msg, readBody);

            return Step.Result.MORE_DATA;
        }

        private Step.Result readBody()
        {
            nextStep(header, 10, readHeader);
            return Step.Result.DECODED;
        }
    }

    @Test
    public void testCustomDecoder()
    {
        CustomDecoder cdecoder = new CustomDecoder(32, 64);

        ByteBuffer in = cdecoder.getBuffer();
        int insize = readHeader(in);
        assertThat(insize, is(10));
        readBody(in);

        in.flip();
        ValueReference<Integer> processed = new ValueReference<>(0);
        Step.Result result = cdecoder.decode(in, 30, processed);
        assertThat(processed.get(), is(30));
        assertThat(cdecoder.size, is(20));
        assertThat(result, is(Step.Result.DECODED));
    }

    private void readBody(ByteBuffer in)
    {
        in.put("1234567890".getBytes(ZMQ.CHARSET));
        in.put("1234567890".getBytes(ZMQ.CHARSET));
    }

    private int readHeader(ByteBuffer in)
    {
        in.put("HEADER".getBytes(ZMQ.CHARSET));
        in.putInt(20);
        return in.position();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAssignCustomDecoder()
    {
        Ctx ctx = ZMQ.createContext();

        SocketBase socket = ctx.createSocket(ZMQ.ZMQ_PAIR);

        boolean rc = socket.setSocketOpt(ZMQ.ZMQ_DECODER, CustomDecoder.class);
        assertThat(rc, is(true));

        ZMQ.close(socket);
        ZMQ.term(ctx);
    }

    private static class WrongDecoder extends CustomDecoder
    {
        public WrongDecoder(int bufsize)
        {
            super(bufsize, 0);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAssignWrongCustomDecoder()
    {
        Ctx ctx = ZMQ.createContext();

        SocketBase socket = ctx.createSocket(ZMQ.ZMQ_PAIR);

        boolean rc = socket.setSocketOpt(ZMQ.ZMQ_DECODER, WrongDecoder.class);
        assertThat(rc, is(false));

        ZMQ.close(socket);
        ZMQ.term(ctx);
    }
}
