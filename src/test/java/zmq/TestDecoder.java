/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

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
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class TestDecoder
{
    DecoderBase decoder;
    Helper.DummySession session;

    @Before
    public void setUp()
    {
        session = new DummySession();
        decoder = new Decoder(64, 256);
        decoder.setMsgSink(session);
    }

    // as if it read data from socket
    private int readShortMessage(ByteBuffer buf)
    {
        buf.put((byte) 6);
        buf.put((byte) 0); // flag
        buf.put("hello".getBytes(ZMQ.CHARSET));

        return buf.position();
    }

    // as if it read data from socket
    private int readLongMessage1(ByteBuffer buf)
    {
        buf.put((byte) 201);
        buf.put((byte) 0); // flag
        for (int i = 0; i < 6; i++) {
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        buf.put("01".getBytes(ZMQ.CHARSET));
        return buf.position();
    }

    private int readLongMessage2(ByteBuffer buf)
    {
        buf.put("23456789".getBytes(ZMQ.CHARSET));
        for (int i = 0; i < 13; i++) {
            buf.put("0123456789".getBytes(ZMQ.CHARSET));
        }
        return buf.position();
    }

    @Test
    public void testReader()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readShortMessage(in);

        assertThat(insize, is(7));
        in.flip();
        int process = decoder.processBuffer(in, insize);
        assertThat(process, is(7));
    }

    @Test
    public void testReaderLong()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readLongMessage1(in);

        assertThat(insize, is(64));
        in.flip();
        int process = decoder.processBuffer(in, insize);
        assertThat(process, is(64));

        in = decoder.getBuffer();
        assertThat(in.capacity(), is(200));
        assertThat(in.position(), is(62));

        insize = readLongMessage2(in);

        assertThat(insize, is(200));
        process = decoder.processBuffer(in, 138);
        assertThat(process, is(138));
        assertThat(in.array()[199], is((byte) '9'));
    }

    @Test
    public void testReaderMultipleMsg()
    {
        ByteBuffer in = decoder.getBuffer();
        int insize = readShortMessage(in);
        assertThat(insize, is(7));
        readShortMessage(in);

        in.flip();
        int processed = decoder.processBuffer(in, 14);
        assertThat(processed, is(14));
        assertThat(in.position(), is(14));

        assertThat(session.out.size(), is(2));
    }

    static class CustomDecoder extends DecoderBase
    {
        private static final int READ_HEADER = 0;
        private static final int READ_BODY = 1;

        byte[] header = new byte[10];
        Msg msg;
        int size = -1;
        IMsgSink sink;

        public CustomDecoder(int bufsize, long maxmsgsize)
        {
            super(bufsize);
            nextStep(header, 10, READ_HEADER);
        }

        @Override
        protected boolean next()
        {
            switch (state()) {
            case READ_HEADER:
                return readHeader();
            case READ_BODY:
                return readBody();
            }
            return false;
        }

        private boolean readHeader()
        {
            assertThat(new String(header, 0, 6, ZMQ.CHARSET), is("HEADER"));
            ByteBuffer b = ByteBuffer.wrap(header, 6, 4);
            size = b.getInt();

            msg = new Msg(size);
            nextStep(msg, READ_BODY);

            return true;
        }

        private boolean readBody()
        {
            sink.pushMsg(msg);
            nextStep(header, 10, READ_HEADER);
            return true;
        }

        @Override
        public boolean stalled()
        {
            return state() == READ_BODY;
        }

        @Override
        public void setMsgSink(IMsgSink msgSink)
        {
            sink = msgSink;
        }
    }

    @Test
    public void testCustomDecoder()
    {
        CustomDecoder cdecoder = new CustomDecoder(32, 64);
        cdecoder.setMsgSink(session);

        ByteBuffer in = cdecoder.getBuffer();
        int insize = readHeader(in);
        assertThat(insize, is(10));
        readBody(in);

        in.flip();
        int processed = cdecoder.processBuffer(in, 30);
        assertThat(processed, is(30));
        assertThat(cdecoder.size, is(20));
        assertThat(session.out.size(), is(1));
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
}
