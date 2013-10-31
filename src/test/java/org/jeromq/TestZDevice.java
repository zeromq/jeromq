/*
    Copyright other contributors as noted in the AUTHORS file.
                
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
package org.jeromq;

import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jeromq.ZMQ;
import org.jeromq.ZMQ.Context;
import org.jeromq.ZMQ.Socket;
import org.jeromq.ZMQ.Msg;

public class TestZDevice {

    static class Client extends Thread {
        
        private Socket s = null;
        private String name = null;
        public Client (Context ctx, String name_) {
            s = ctx.socket(ZMQ.REQ);
            name = name_;
            
            s.setIdentity(name);
        }
        
        @Override
        public void run () {
            System.out.println("Start client thread " + name);
            s.connect( "tcp://127.0.0.1:6660");
            s.send("hellow", 0);
            Msg msg = s.recvMsg(0);
            System.out.println(name + " got response1 " + new String(msg.data(), ZMQ.CHARSET));
            s.send("world cup", 0);
            msg = s.recvMsg(0);
            System.out.println(name + " got response2 " + new String(msg.data(), ZMQ.CHARSET));

            s.close();
            System.out.println("Stop client thread " + name);
        }
    }
    
    static class Dealer extends Thread {
        
        private Socket s = null;
        private String name = null;
        public Dealer(Context ctx, String name_) {
            s = ctx.socket(ZMQ.DEALER);
            name = name_;
            
            s.setIdentity(name);
        }
        
        @Override
        public void run () {
            
            System.out.println("Start dealer " + name);
            
            s.connect( "tcp://127.0.0.1:6661");
            int count = 0;
            while (count<2) {
                Msg msg = s.recvMsg(0);
                if (msg == null) {
                    throw new RuntimeException();
                }
                String identity = new String(msg.data(), 0 , msg.size(), ZMQ.CHARSET);
                System.out.println(name + " recieved cleint identity " + identity);
                msg = s.recvMsg(0);
                if (msg == null) {
                    throw new RuntimeException();
                }
                System.out.println(name + " recieved bottom " + msg);
                
                msg = s.recvMsg(0);
                if (msg == null) {
                    throw new RuntimeException();
                }
                String data = new String(msg.data(), 0 , msg.size(), ZMQ.CHARSET);

                System.out.println(name + " recieved data " + msg + " " + data);
                s.send(identity, ZMQ.SNDMORE);
                s.send((byte[])null, ZMQ.SNDMORE);
                
                Msg response = null;
                response = new Msg(msg.size() + 3);
                response.put("OK ", 0);
                response.put(msg.data(), 3);
                
                s.send(response, 0);
                count++;
            }
            s.close();
            System.out.println("Stop dealer " + name);
        }
    }
    static class Main extends Thread {
        
        Context ctx;
        Main(Context ctx_) {
            ctx = ctx_;
        }
        
        @Override
        public void run() {
            int port;
            Socket sa = ctx.socket(ZMQ.ROUTER);
            
            assert (sa != null);
            port = sa.bind ("tcp://127.0.0.1:6660");
            assertEquals (port, 6660);

            
            Socket sb = ctx.socket(ZMQ.ROUTER);
            assert (sb != null);
            port = sb.bind ("tcp://127.0.0.1:6661");
            assertEquals (port, 6661);
            
            ArrayList<byte[]> ids = new ArrayList<byte[]>();
            ids.add(new byte[]{'A','A'});
            ids.add(new byte[]{'B','B'});
            ZDevice.addressDevice(sa, sb, ids);

            sa.close();
            sb.close();

        }
        
    }

    @Test
    public void testAddressDevice()  throws Exception {
        Context ctx = ZMQ.context(1);
        assert (ctx!= null);

        Main mt = new Main(ctx);
        mt.start();
        new Dealer(ctx, "AA").start();
        new Dealer(ctx, "BB").start();
        
        Thread.sleep(1000);
        Thread c1 = new Client(ctx, "X");
        c1.start();

        Thread c2 = new Client(ctx, "Y");
        c2.start();
        
        c1.join();
        c2.join();
        
        ctx.term();
    }

}
