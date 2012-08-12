package zmq;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.TestUtil.DummySession;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestDecoder {
    
    DecoderBase decoder ;
    TestUtil.DummySession session;
    
    @Before
    public void setUp () {
        session = new DummySession();
        decoder = new Decoder(64, 128);
        decoder.set_session(session);
    }
    // as if it read data from socket
    private int read_short_message (ByteBuffer buf) {
        buf.put((byte)6);
        buf.put((byte)0); // flag
        buf.put("hello".getBytes());
        
        return buf.position();
    }
    
    @Test
    public void testReader() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_short_message (in);
        
        assertThat(insize, is(7));
        int remaining = decoder.process_buffer (in);
        assertThat(remaining, is(0));
    }

    @Test
    public void testReaderMultipleMsg() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_short_message (in);
        assertThat(insize, is(7));
        read_short_message (in);
        
        int remaining = decoder.process_buffer (in);
        assertThat(remaining, is(0));
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
        
        int remaining = cdecoder.process_buffer (in);
        assertThat(remaining, is(0));
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
