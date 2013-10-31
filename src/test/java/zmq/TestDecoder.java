/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2011 250bpm s.r.o.
    Copyright (c) 2007-2011 Other contributors as noted in the AUTHORS file
                
    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.
            
    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.
        
    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package zmq;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.Helper.DummySession;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestDecoder {
    
    DecoderBase decoder ;
    Helper.DummySession session;
    
    @Before
    public void setUp () 
    {
        session = new DummySession();
        decoder = new Decoder(64, 256);
        decoder.set_msg_sink (session);
    }
    // as if it read data from socket
    private int read_short_message (ByteBuffer buf) {
        buf.put((byte)6);
        buf.put((byte)0); // flag
        buf.put("hello".getBytes(ZMQ.CHARSET));
        
        return buf.position();
    }
    
    // as if it read data from socket
    private int read_long_message1 (ByteBuffer buf) {
        
        buf.put((byte)201);
        buf.put((byte)0); // flag
        for (int i=0; i < 6; i++)
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        buf.put("01".getBytes(ZMQ.CHARSET));
        return buf.position();
    }

    private int read_long_message2 (ByteBuffer buf) {
        buf.put("23456789".getBytes(ZMQ.CHARSET));        
        for (int i=0; i < 13; i++)
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
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

        private final static int read_header = 0;
        private final static int read_body = 1;
        
        byte [] header = new byte [10];
        Msg msg;
        int size = -1;
        IMsgSink sink;
        
        public CustomDecoder (int bufsize_, long maxmsgsize_) 
        {
            super(bufsize_);
            next_step(header, 10, read_header);
        }

        @Override
        protected boolean next () {
            switch (state()) {
            case read_header:
                return read_header ();
            case read_body:
                return read_body ();
            }
            return false;
        }

        private boolean read_header () {
            
            assertThat (new String (header, 0, 6, ZMQ.CHARSET), is ("HEADER"));
            ByteBuffer b = ByteBuffer.wrap (header, 6, 4);
            size = b.getInt();
            
            msg = new Msg(size);
            next_step(msg, read_body);
            
            return true;
        }

        private boolean read_body() 
        {
            sink.push_msg (msg);
            next_step(header, 10, read_header);
            return true;
        }

        @Override
        public boolean stalled() {
            return state() == read_body;
        }

        @Override
        public void set_msg_sink (IMsgSink msg_sink)
        {
            sink = msg_sink;
        }
        
    }
    @Test
    public void testCustomDecoder () 
    {
        CustomDecoder cdecoder = new CustomDecoder(32, 64);
        cdecoder.set_msg_sink (session);
        
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
        in.put("1234567890".getBytes(ZMQ.CHARSET));
        in.put("1234567890".getBytes(ZMQ.CHARSET));
    }
    private int read_header(ByteBuffer in) {
        in.put("HEADER".getBytes(ZMQ.CHARSET));
        in.putInt(20);
        return in.position();
    }
}
