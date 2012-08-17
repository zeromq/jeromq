package zmq;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.TestHelper.DummySession;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestDecoder {
    
    DecoderBase decoder ;
    TestHelper.DummySession session;
    
    @Before
    public void setUp () {
        session = new DummySession();
        decoder = new Decoder(64, 256);
        decoder.set_session(session);
    }
    // as if it read data from socket
    private int read_short_message (ByteBuffer buf) {
        buf.put((byte)6);
        buf.put((byte)0); // flag
        buf.put("hello".getBytes());
        
        return buf.position();
    }
    
    // as if it read data from socket
    private int read_long_message1 (ByteBuffer buf) {
        
        buf.put((byte)201);
        buf.put((byte)0); // flag
        for (int i=0; i < 6; i++)
            buf.put("0123456789".getBytes());
        buf.put("01".getBytes());
        return buf.position();
    }

    private int read_long_message2 (ByteBuffer buf) {
        buf.put("23456789".getBytes());        
        for (int i=0; i < 13; i++)
            buf.put("0123456789".getBytes());
        return buf.position();
    }

    @Test
    public void testReader() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_short_message (in);
        
        assertThat(insize, is(7));
        in.flip();
        int process = decoder.process_buffer (in, insize);
        assertThat(process, is(7));
    }
    
    @Test
    public void testReaderLong() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_long_message1 (in);
        
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
}
