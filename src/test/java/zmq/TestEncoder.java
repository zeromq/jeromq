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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import zmq.Helper.DummySession;
import zmq.Helper.DummySocketChannel;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestEncoder {
    
    EncoderBase encoder ;
    Helper.DummySession session;
    DummySocketChannel sock;
    @Before
    public void setUp () {
        session = new DummySession();
        encoder = new Encoder(64);
        encoder.set_session(session);
        sock = new DummySocketChannel();
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
        return msg;
    }

    @Test
    public void testReader() {
        
        Msg msg = read_short_message();
        session.write(msg);
        Transfer out = encoder.get_data ();
        int outsize = out.remaining();
        
        assertThat(outsize, is(7));
        int written = write(out);
        assertThat(written, is(7));
        int remaning = out.remaining();
        assertThat(remaning, is(0));
    }
    
    private int write(Transfer out) {
        
        try {
            return out.transferTo(sock);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    @Test
    public void testReaderLong() {
        Msg msg = read_long_message1();
        session.write(msg);
        Transfer out = encoder.get_data ();

        int insize = out.remaining();
        
        assertThat(insize, is(64));
        int written = write(out);
        assertThat(written, is(64));

        out = encoder.get_data ();
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

        private final static int read_header = 0;
        private final static int read_body = 1;
        
        ByteBuffer header = ByteBuffer.allocate(10);
        Msg msg;
        int size = -1;
        
        public CustomEncoder(int bufsize_) {
            super(bufsize_);
            next_step(null, read_body, true);
        }

        @Override
        protected boolean next() {
            switch (state()) {
            case read_header:
                return read_header();
            case read_body:
                return read_body();
            }
            return false;
        }

        private boolean read_header() {
            next_step (msg.data (), msg.size (),
                    read_body, !msg.has_more());
            return true;
                
        }

        private boolean read_body() {
            
            msg = session.read();
            
            if (msg == null) {
                return false;
            }
            header.clear();
            header.put("HEADER".getBytes());
            header.putInt(msg.size());
            header.flip();
            next_step(header, 10, read_header, !msg.has_more());
            return true;
        }

        
    }
    @Test
    public void testCustomDecoder () {
        
        CustomEncoder cencoder = new CustomEncoder(32);
        cencoder.set_session(session);
        Msg msg = new Msg("12345678901234567890");
        session.write(msg);
        
        Transfer out = cencoder.get_data ();
        write(out);
        byte[] data = sock.data();

        assertThat(new String(data,0, 6), is("HEADER"));
        assertThat((int)data[9], is(20));
        assertThat(new String(data,10, 20), is("12345678901234567890"));
        
    }
}
