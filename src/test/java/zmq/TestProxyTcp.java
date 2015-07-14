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

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestProxyTcp
{
    static class Client extends Thread
    {
        public Client()
        {
        }

        @Override
        public void run()
        {
            System.out.println("Start client thread");
            try {
                Socket s = new Socket("127.0.0.1", 6560);
                Helper.send(s, "hellow");
                Helper.send(s, "1234567890abcdefghizklmnopqrstuvwxyz");
                Helper.send(s, "end");
                Helper.send(s, "end");
                s.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Stop client thread");
        }
    }

    static class Dealer extends Thread
    {
        private final SocketBase s;
        private final String name;

        public Dealer(Ctx ctx, String name)
        {
            this.s = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            this.name = name;
        }

        @Override
        public void run()
        {
            System.out.println("Start dealer " + name);

            ZMQ.connect(s, "tcp://127.0.0.1:6561");
            int i = 0;
            while (true) {
                Msg msg = s.recv(0);
                if (msg == null) {
                    throw new RuntimeException("hello");
                }
                System.out.println("REP recieved " + msg);
                String data = new String(msg.data(), 0, msg.size(), ZMQ.CHARSET);

                Msg response = null;
                if ((i % 3) == 2) {
                    response = new Msg(msg.size() + 3);
                    response.put("OK ".getBytes(ZMQ.CHARSET))
                            .put(msg.data());
                }
                else {
                    response = new Msg(msg.data());
                }

                s.send(response, (i % 3) == 2 ? 0 : ZMQ.ZMQ_SNDMORE);
                i++;
                if (data.equals("end")) {
                    break;
                }
            }
            s.close();
            System.out.println("Stop dealer " + name);
        }
    }

    static class ProxyDecoder extends DecoderBase
    {
        private static final int READ_HEADER = 0;
        private static final int READ_BODY = 1;

        byte [] header = new byte [4];
        Msg msg;
        int size = -1;
        boolean identitySent = false;
        Msg bottom;
        IMsgSink msgSink;

        public ProxyDecoder(int bufsize, long maxmsgsize)
        {
            super(bufsize);
            nextStep(header, 4, READ_HEADER);

            bottom = new Msg();
            bottom.setFlags(Msg.MORE);
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
            size = Integer.parseInt(new String(header, ZMQ.CHARSET));
            System.out.println("Received " + size);
            msg = new Msg(size);
            nextStep(msg, READ_BODY);

            return true;
        }

        private boolean readBody()
        {
            if (msgSink == null) {
                return false;
            }

            System.out.println("Received body " + new String(msg.data(), ZMQ.CHARSET));

            if (!identitySent) {
                Msg identity = new Msg();
                msgSink.pushMsg(identity);
                identitySent = true;
            }

            msgSink.pushMsg(bottom);
            msgSink.pushMsg(msg);

            nextStep(header, 4, READ_HEADER);
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
            this.msgSink = msgSink;
        }
    }

    static class ProxyEncoder extends EncoderBase
    {
        public static final boolean RAW_ENCODER = true;
        private static final int WRITE_HEADER = 0;
        private static final int WRITE_BODY = 1;

        ByteBuffer header = ByteBuffer.allocate(4);
        Msg msg;
        int size = -1;
        boolean messageReady;
        boolean identityRecieved;
        IMsgSource msgSource;

        public ProxyEncoder(int bufsize)
        {
            super(bufsize);
            nextStep(null, WRITE_HEADER, true);
            messageReady = false;
            identityRecieved = false;
        }

        @Override
        protected boolean next()
        {
            switch (state()) {
            case WRITE_HEADER:
                return writeHeader();
            case WRITE_BODY:
                return writeBody();
            }
            return false;
        }

        private boolean writeBody()
        {
            System.out.println("writer body ");
            nextStep(msg, WRITE_HEADER, !msg.hasMore());

            return true;
        }

        private boolean writeHeader()
        {
            if (msgSource == null) {
                return false;
            }

            msg = msgSource.pullMsg();

            if (msg == null) {
                return false;
            }
            if (!identityRecieved) {
                identityRecieved = true;
                nextStep(header.array(), msg.size() < 255 ? 2 : 10, WRITE_BODY, false);
                return true;
            }
            else
            if (!messageReady) {
                messageReady = true;
                msg = msgSource.pullMsg();

                if (msg == null) {
                    return false;
                }
            }
            messageReady = false;
            System.out.println("write header " + msg.size());

            header.clear();
            header.put(String.format("%04d", msg.size()).getBytes(ZMQ.CHARSET));
            header.flip();
            nextStep(header.array(), 4, WRITE_BODY, false);
            return true;
        }

        @Override
        public void setMsgSource(IMsgSource msgSource)
        {
            this.msgSource = msgSource;
        }
    }

    static class Main extends Thread
    {
        private Ctx ctx;
        private Selector selector;
        Main(Ctx ctx)
        {
            this.ctx = ctx;
        }

        @Override
        public void run()
        {
            boolean rc;
            SocketBase sa = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
            assertThat(sa, notNullValue());

            sa.setSocketOpt(ZMQ.ZMQ_DECODER, ProxyDecoder.class);
            sa.setSocketOpt(ZMQ.ZMQ_ENCODER, ProxyEncoder.class);

            rc = ZMQ.bind(sa, "tcp://127.0.0.1:6560");
            assertThat(rc, is(true));

            SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            assertThat(sb, notNullValue());
            rc = ZMQ.bind(sb, "tcp://127.0.0.1:6561");
            assertThat(rc, is(true));

            ZMQ.proxy(sa, sb, null);

            ZMQ.close(sa);
            ZMQ.close(sb);
        }
    }

    @Test
    public void testProxyTcp() throws Exception
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        Main mt = new Main(ctx);
        mt.start();
        new Dealer(ctx, "A").start();
        new Dealer(ctx, "B").start();

        Thread.sleep(1000);
        Thread client = new Client();
        client.start();

        client.join();

        ZMQ.term(ctx);
    }
}
