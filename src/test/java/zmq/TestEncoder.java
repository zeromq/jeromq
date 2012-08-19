package zmq;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.TestHelper.DummySession;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestEncoder {
    
    EncoderBase encoder ;
    TestHelper.DummySession session;
    
    @Before
    public void setUp () {
        session = new DummySession();
        encoder = new Encoder(64);
        encoder.set_session(session);
    }
    // as if it read data from socket
    private Msg read_short_message () {
        Msg msg = new Msg(5);
        msg.put("hello".getBytes(),0);
        
        return msg;
    }
    
    // as if it read data from socket
    private Msg read_long_message1 () {
        
        Msg msg = new Msg(200);
        for (int i=0; i < 20; i++)
            msg.put("0123456789".getBytes(), i*10);
        //msg.put("01".getBytes(),60);
        return msg;
    }

    private int read_long_message2 (ByteBuffer buf) {
        buf.put("23456789".getBytes());        
        for (int i=0; i < 13; i++)
            buf.put("0123456789".getBytes());
        return buf.position();
    }

    @Test
    public void testReader() {
        
        Msg msg = read_short_message();
        session.write(msg);
        Transfer out = encoder.get_data ();
        int outsize = out.remaining();
        
        assertThat(outsize, is(7));
        int remaning = write(out);
        assertThat(remaning, is(0));
    }
    
    private int write(Transfer out) {
        return 0;
    }
    
    @Test
    public void testReaderLong() {
        Msg msg = read_long_message1();
        session.write(msg);
        Transfer out = encoder.get_data ();

        int insize = out.remaining();
        
        assertThat(insize, is(64));
        in.flip();
        int process = decoder.process_buffer (in, insize);
        assertThat(process, is(64));

        
        in = decoder.get_buffer ();
        assertThat(in.capacity(), is(200));
        assertThat(in.position(), is(62));

        insize = read_long_message2 (in);
        
        assertThat(insize, is(200));
        process = decoder.process_buffer (in, 138);
        assertThat(process, is(138));
        assertThat(in.array()[199], is((byte)'9'));

    }
    /*

    @Test
    public void testReaderMultipleMsg() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_short_message (in);
        assertThat(insize, is(7));
        read_short_message (in);
        
        in.flip();
        int processed = decoder.process_buffer (in, 14);
        assertThat(processed, is(14));
        assertThat(in.position(), is(14));
        
        assertThat(session.out.size(), is(2));
        
    }
    
    static class CustomDecoder extends DecoderBase
    {

        enum State {
            read_header,
            read_body
        };
        
        ByteBuffer header = ByteBuffer.allocate(10);
        Msg msg;
        int size = -1;
        
        public CustomDecoder(int bufsize_, long maxmsgsize_) {
            super(bufsize_, maxmsgsize_);
            next_step(header, 10, State.read_header);
        }

        @Override
        protected boolean next() {
            switch ((State)state()) {
            case read_header:
                return read_header();
            case read_body:
                return read_body();
            }
            return false;
        }

        private boolean read_header() {
            byte[] h = new byte[6];
            header.get(h, 0, 6);
            
            assertThat(new String(h), is("HEADER"));
            size = header.getInt();
            
            msg = new Msg(size);
            next_step(msg, State.read_body);
            
            return true;
        }

        private boolean read_body() {
            
            session_write(msg);
            header.clear();
            next_step(header, 10, State.read_header);
            return true;
        }

        @Override
        public boolean stalled() {
            return state() == State.read_body;
        }
        
    }
    @Test
    public void testCustomDecoder () {
        
        CustomDecoder cdecoder = new CustomDecoder(32, 64);
        cdecoder.set_session(session);
        
        ByteBuffer in = cdecoder.get_buffer ();
        int insize = read_header (in);
        assertThat(insize, is(10));
        read_body (in);
        
        in.flip();
        int processed = cdecoder.process_buffer (in, 30);
        assertThat(processed, is(30));
        assertThat(cdecoder.size, is(20));
        assertThat(session.out.size(), is(1));
    }
    private void read_body(ByteBuffer in) {
        in.put("1234567890".getBytes());
        in.put("1234567890".getBytes());
    }
    private int read_header(ByteBuffer in) {
        in.put("HEADER".getBytes());
        in.putInt(20);
        return in.position();
    }
    */
}
