package zmq;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.TestUtil.DummySession;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestDecoder {
    
    Decoder decoder ;
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
        in.flip();
        int processed = decoder.process_buffer (in);
        assertThat(processed, is(7));
    }

    @Test
    public void testReaderMultipleMsg() {
        ByteBuffer in = decoder.get_buffer ();
        int insize = read_short_message (in);
        assertThat(insize, is(7));
        read_short_message (in);
        
        in.flip();
        int processed = decoder.process_buffer (in);
        assertThat(processed, is(14));
        
        assertThat(session.out.size(), is(2));
    }
}
