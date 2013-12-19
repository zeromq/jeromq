/*
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2011 iMatix Corporation
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file

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

public class TestProxyTcp {
    
    static class Client extends Thread {
        
        public Client () {
        }
        
        @Override
        public void run () {
            System.out.println("Start client thread");
            try {
                Socket s = new Socket("127.0.0.1", 6560);
                Helper.send(s, "hellow");
                Helper.send(s, "1234567890abcdefghizklmnopqrstuvwxyz");
                Helper.send(s, "end");
                Helper.send(s, "end");
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Stop client thread");
        }
    }
    
    static class Dealer extends Thread {
        
        private SocketBase s = null;
        private String name = null;
        public Dealer(Ctx ctx, String name_) {
            s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);

            name = name_;
        }
        
        @Override
        public void run () {
            
            System.out.println("Start dealer " + name);
            
            ZMQ.zmq_connect(s, "tcp://127.0.0.1:6561");
            int i = 0;
            while (true) {
                Msg msg = s.recv(0);
                if (msg == null) {
                    throw new RuntimeException("hello");
                }
                System.out.println("REP recieved " + msg);
                String data = new String(msg.data(), 0 , msg.size(), ZMQ.CHARSET);

                Msg response = null;
                if (i%3 == 2) {
                    response = new Msg(msg.size() + 3);
                    response.put("OK ".getBytes(ZMQ.CHARSET))
                            .put(msg.data());
                } else {
                    response = new Msg(msg.data());
                }
                
                s.send(response, i%3==2?0:ZMQ.ZMQ_SNDMORE);
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

        private final static int read_header = 0;
        private final static int read_body = 1;
        
        byte [] header = new byte [4];
        Msg msg;
        int size = -1;
        boolean identity_sent = false;
        Msg bottom ;
        IMsgSink msg_sink;
        
        public ProxyDecoder(int bufsize_, long maxmsgsize_) {
            super(bufsize_);
            next_step(header, 4, read_header);
            
            bottom = new Msg();
            bottom.setFlags (Msg.MORE);
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
            size = Integer.parseInt(new String(header, ZMQ.CHARSET));
            System.out.println("Received " + size);
            msg = new Msg(size);
            next_step(msg, read_body);
            
            return true;
        }

        private boolean read_body() {
            
            if (msg_sink == null)
                return false;
            
            System.out.println("Received body " + new String(msg.data(), ZMQ.CHARSET));
            
            if (!identity_sent) {
                Msg identity = new Msg();
                msg_sink.push_msg (identity);
                identity_sent = true;
            }
            
            msg_sink.push_msg (bottom);
            msg_sink.push_msg (msg);
            
            next_step(header, 4, read_header);
            return true;
        }

        @Override
        public boolean stalled() {
            return state() == read_body;
        }

        @Override
        public void set_msg_sink (IMsgSink msg_sink_)
        {
            msg_sink = msg_sink_;
        }
        
    }

    static class ProxyEncoder extends EncoderBase
    {
        public final static boolean RAW_ENCODER = true;
        private final static int write_header = 0;
        private final static int write_body = 1;
        
        ByteBuffer header = ByteBuffer.allocate(4);
        Msg msg;
        int size = -1;
        boolean message_ready;
        boolean identity_recieved;
        IMsgSource msg_source;
        
        public ProxyEncoder(int bufsize_) {
            super(bufsize_);
            next_step(null, write_header, true);
            message_ready = false;
            identity_recieved = false;
        }

        @Override
        protected boolean next() {
            switch (state()) {
            case write_header:
                return write_header();
            case write_body:
                return write_body();
            }
            return false;
        }

        private boolean write_body() {
            System.out.println("writer body ");
            next_step(msg, write_header, !msg.hasMore());
            
            return true;
        }

        private boolean write_header () 
        {
            if (msg_source == null)
                return false;
            
            msg = msg_source.pull_msg ();
            
            if (msg == null) {
                return false;
            }
            if (!identity_recieved) {
                identity_recieved = true;
                next_step (header.array (), msg.size () < 255 ? 2 : 10, write_body, false);
                return true;
            }
            else
            if (!message_ready) {
                message_ready = true;
                msg = msg_source.pull_msg ();
                
                if (msg == null) {
                    return false;
                }
            }
            message_ready = false;
            System.out.println ("write header " + msg.size());

            header.clear ();
            header.put (String.format("%04d", msg.size()).getBytes(ZMQ.CHARSET));
            header.flip ();
            next_step (header.array (), 4, write_body, false);
            return true;
        }

        @Override
        public void set_msg_source (IMsgSource msg_source_)
        {
            msg_source = msg_source_;
        }

        
    }
    
    static class Main extends Thread {
        
        Ctx ctx;
        Selector selector;
        Main(Ctx ctx_) {
            ctx = ctx_;
        }
        
        @Override
        public void run() {
            boolean rc ;
            SocketBase sa = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
            assert (sa != null);
            
            sa.setsockopt(ZMQ.ZMQ_DECODER, ProxyDecoder.class);
            sa.setsockopt(ZMQ.ZMQ_ENCODER, ProxyEncoder.class);
            
            
            rc = ZMQ.zmq_bind (sa, "tcp://127.0.0.1:6560");
            assert (rc );

            
            SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);
            assert (sb != null);
            rc = ZMQ.zmq_bind (sb, "tcp://127.0.0.1:6561");
            assert (rc );
            
            ZMQ.zmq_proxy (sa, sb, null);

            ZMQ.zmq_close (sa);
            ZMQ.zmq_close (sb);

        }
        
    }

    @Test
    public void testProxyTcp()  throws Exception {
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx!= null);

        Main mt = new Main(ctx);
        mt.start();
        new Dealer(ctx, "A").start();
        new Dealer(ctx, "B").start();
        
        Thread.sleep(1000);
        Thread client = new Client();
        client.start();

        client.join();
        
        ZMQ.zmq_term(ctx);
    }
}
