package zmq.io.coder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.util.Errno;
import zmq.util.ValueReference;

public class CustomEncoderTest
{
    private final Helper.DummySocketChannel sock = new Helper.DummySocketChannel();

    private int write(ByteBuffer out)
    {
        return sock.write(out);
    }

    static class CustomEncoder extends EncoderBase
    {
        public static final boolean RAW_ENCODER = true;
        private final Runnable      readHeader  = this::readHeader;
        private final Runnable      readBody    = this::readBody;

        final ByteBuffer header = ByteBuffer.allocate(10);

        public CustomEncoder(int bufsize, long maxmsgsize)
        {
            super(new Errno(), bufsize);
            initStep(readBody, true);
        }

        private void readHeader()
        {
            nextStep(inProgress, readBody, !inProgress.hasMore());
        }

        private void readBody()
        {
            if (inProgress == null) {
                return;
            }
            header.clear();
            header.put("HEADER".getBytes(ZMQ.CHARSET));
            header.putInt(inProgress.size());
            header.flip();
            nextStep(header, header.limit(), readHeader, false);
        }

    }

    @Test
    public void testCustomEncoder()
    {
        CustomEncoder cencoder = new CustomEncoder(32, Integer.MAX_VALUE / 2);

        Msg msg = new Msg("12345678901234567890".getBytes(ZMQ.CHARSET));
        cencoder.loadMsg(msg);
        ValueReference<ByteBuffer> ref = new ValueReference<>();
        int outsize = cencoder.encode(ref, 0);
        assertThat(outsize, is(30));
        ByteBuffer out = ref.get();
        out.flip();
        write(out);
        byte[] data = sock.data();

        assertThat(new String(data, 0, 6, ZMQ.CHARSET), is("HEADER"));
        assertThat((int) data[9], is(20));
        assertThat(new String(data, 10, 20, ZMQ.CHARSET), is("12345678901234567890"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAssignCustomEncoder()
    {
        Ctx ctx = ZMQ.createContext();

        SocketBase socket = ctx.createSocket(ZMQ.ZMQ_PAIR);

        boolean rc = socket.setSocketOpt(ZMQ.ZMQ_ENCODER, CustomEncoder.class);
        assertThat(rc, is(true));

        ZMQ.close(socket);
        ZMQ.term(ctx);
    }

    private static class WrongEncoder extends CustomEncoder
    {
        public WrongEncoder(int bufsize)
        {
            super(bufsize, 0);
        }
    }

    @SuppressWarnings("deprecation")
    @Test(expected = ZError.InstantiationException.class)
    public void testAssignWrongCustomEncoder()
    {
        Ctx ctx = ZMQ.createContext();
        SocketBase socket = ctx.createSocket(ZMQ.ZMQ_PAIR);

        try {
            socket.setSocketOpt(ZMQ.ZMQ_ENCODER, WrongEncoder.class);
        }
        finally {
            ZMQ.close(socket);
            ZMQ.term(ctx);
        }
    }
}
