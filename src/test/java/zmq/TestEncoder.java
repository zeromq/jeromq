package zmq;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.Helper.DummySession;
import zmq.Helper.DummySocketChannel;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class TestEncoder
{
    EncoderBase encoder;
    Helper.DummySession session;
    DummySocketChannel sock;
    @Before
    public void setUp()
    {
        session = new DummySession();
        encoder = new Encoder(64);
        encoder.setMsgSource(session);
        sock = new DummySocketChannel();
    }
    // as if it read data from socket
    private Msg readShortMessage()
    {
        Msg msg = new Msg("hello".getBytes(ZMQ.CHARSET));
        return msg;
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
        session.pushMsg(msg);
        Transfer out = encoder.getData(null);
        int outsize = out.remaining();

        assertThat(outsize, is(7));
        int written = write(out);
        assertThat(written, is(7));
        int remaning = out.remaining();
        assertThat(remaning, is(0));
    }

    private int write(Transfer out)
    {
        try {
            return out.transferTo(sock);
        }
        catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Test
    public void testReaderLong()
    {
        Msg msg = readLongMessage1();
        session.pushMsg(msg);
        Transfer out = encoder.getData(null);

        int insize = out.remaining();

        assertThat(insize, is(64));
        int written = write(out);
        assertThat(written, is(64));

        out = encoder.getData(null);
        int remaning = out.remaining();
        assertThat(remaning, is(138));

        written = write(out);
        assertThat(written, is(64));

        remaning = out.remaining();
        assertThat(remaning, is(74));

        written = write(out);
        assertThat(written, is(64));

        remaning = out.remaining();
        assertThat(remaning, is(10));

        written = write(out);
        assertThat(written, is(10));

        remaning = out.remaining();
        assertThat(remaning, is(0));

    }

    static class CustomEncoder extends EncoderBase
    {
        public static final boolean RAW_ENCODER = true;
        private static final int read_header = 0;
        private static final int read_body = 1;

        ByteBuffer header = ByteBuffer.allocate(10);
        Msg msg;
        int size = -1;
        IMsgSource source;

        public CustomEncoder(int bufsize)
        {
            super(bufsize);
            nextStep((Msg) null, read_body, true);
        }

        @Override
        protected boolean next()
        {
            switch (state()) {
            case read_header:
                return readHeader();
            case read_body:
                return readBody();
            }
            return false;
        }

        private boolean readHeader()
        {
            nextStep(msg.data(), msg.size(),
                    read_body, !msg.hasMore());
            return true;
        }

        private boolean readBody()
        {
            msg = source.pullMsg();

            if (msg == null) {
                return false;
            }
            header.clear();
            header.put("HEADER".getBytes(ZMQ.CHARSET));
            header.putInt(msg.size());
            header.flip();
            nextStep(header.array(), 10, read_header, !msg.hasMore());
            return true;
        }

        @Override
        public void setMsgSource(IMsgSource msgSource)
        {
            source = msgSource;
        }
    }

    @Test
    public void testCustomDecoder()
    {
        CustomEncoder cencoder = new CustomEncoder(32);
        cencoder.setMsgSource(session);
        Msg msg = new Msg("12345678901234567890".getBytes(ZMQ.CHARSET));
        session.pushMsg(msg);

        Transfer out = cencoder.getData(null);
        write(out);
        byte[] data = sock.data();

        assertThat(new String(data, 0, 6, ZMQ.CHARSET), is("HEADER"));
        assertThat((int) data[9], is(20));
        assertThat(new String(data, 10, 20, ZMQ.CHARSET), is("12345678901234567890"));
    }
}
