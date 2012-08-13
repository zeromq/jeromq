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

import org.junit.Test;

import zmq.TestDecoder.CustomDecoder.State;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestProxyTcp {
    
    static class Client extends Thread {
        
        public Client () {
        }
        
        @Override
        public void run () {
            System.out.println("Start client thread");
            try {
                Socket s = new Socket("127.0.0.1", 5560);
                TestUtil.send(s, "hellow");
                TestUtil.send(s, "1234567890abcdefghizklmnopqrstuvwxyz");
                TestUtil.send(s, "end");
                TestUtil.send(s, "end");
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Stop client thread");
            ZMQ.zmq_poll_stop();
        }
    }
    
    static class Dealer extends Thread {
        
        private SocketBase s = null;
        private String name = null;
        public Dealer(Ctx ctx, String name_) {
            s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);
//            s.setsockopt(ZMQ.ZMQ_IDENTITY, name_);
//            s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REP);
            //s.setsockopt(ZMQ.ZMQ_IDENTITY, name_);

            name = name_;
        }
        
        @Override
        public void run () {
            
            System.out.println("Start dealer " + name);
            
            ZMQ.zmq_connect(s, "tcp://127.0.0.1:5561");
            int i = 0;
            while (true) {
                Msg msg = s.recv(0);
                if (msg == null) {
                    throw new RuntimeException("hello");
                }
                System.out.println("REP recieved " + msg);
                String data = new String(msg.data().array(), 0 , msg.size());

                Msg response = null;
                if (i%3 == 2) {
                    response = new Msg(msg.size() + 3);
                    response.put("OK ");
                } else {
                    response = new Msg(msg.size());
                }
                response.put(msg.data());
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

        enum State {
            read_header,
            read_body
        };
        
        ByteBuffer header = ByteBuffer.allocate(4);
        Msg msg;
        int size = -1;
        
        public ProxyDecoder(int bufsize_, long maxmsgsize_) {
            super(bufsize_, maxmsgsize_);
            next_step(header, 4, State.read_header);
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
            byte[] h = new byte[4];
            header.get(h, 0, 4);
            size = Integer.parseInt(new String(h));
            System.out.println("Received " + size);
            msg = new Msg(size);
            next_step(msg, State.read_body);
            
            return true;
        }

        private boolean read_body() {
            
            System.out.println("Received body " + new String(msg.bytes()));
            session_write(msg);
            
            next_step(header, 4, State.read_header);
            return true;
        }

        @Override
        public boolean stalled() {
            return state() == State.read_body;
        }
        
    }

    static class ProxyEncoder extends EncoderBase
    {

        enum State {
            write_header,
            write_body
        };
        
        ByteBuffer header = ByteBuffer.allocate(4);
        Msg msg;
        int size = -1;
        
        public ProxyEncoder(int bufsize_) {
            super(bufsize_);
            next_step(null, 0, State.write_header, true);
        }

        @Override
        protected boolean next() {
            switch ((State)state()) {
            case write_header:
                return write_header();
            case write_body:
                return write_body();
            }
            return false;
        }

        private boolean write_body() {
            System.out.println("writer body ");
            next_step(msg, State.write_header, !msg.has_more());
            
            return true;
        }

        private boolean write_header() {
            
            msg = session_read();
            if (msg == null) {
                //System.out.println("no data yet");
                //try {
                //    Thread.sleep(1000);
                //} catch (InterruptedException e) {
               // }
                //return false;
                return false;
            }
            System.out.println("write header " + msg.size());

            header.clear();
            header.put(String.format("%04d", msg.size()).getBytes());
            header.flip();
            
            next_step(header, 4, State.write_body, false);
            return true;
        }

        
    }

    @Test
    public void testProxyTcp()  throws Exception {
        Ctx ctx = ZMQ.zmq_init (1);
        assert (ctx!= null);
        
        boolean rc ;

        SocketBase sa = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PROXY);
        sa.setsockopt(ZMQ.ZMQ_DECODER, ProxyDecoder.class);
        sa.setsockopt(ZMQ.ZMQ_ENCODER, ProxyEncoder.class);
        
        assert (sa != null);
        rc = ZMQ.zmq_bind (sa, "tcp://127.0.0.1:5560");
        assert (rc );

        
        SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_DEALER);
        assert (sb != null);
        rc = ZMQ.zmq_bind (sb, "tcp://127.0.0.1:5561");
        assert (rc );


        

        new Dealer(ctx, "A").start();
        new Dealer(ctx, "B").start();
        
        Thread.sleep(1000);
        new Client().start();

        ZMQ.zmq_device (ZMQ.ZMQ_QUEUE, sa, sb);

        
        ZMQ.zmq_close (sa);
        ZMQ.zmq_close (sb);
        
        ZMQ.zmq_term(ctx);
    }
}
