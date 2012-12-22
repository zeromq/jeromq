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


import org.junit.Test;
import static org.junit.Assert.*;

public class TestConnectDelay {

    private static class Server extends Thread 
    {
        @Override
        public void run () 
        {
            Ctx context = ZMQ.zmq_init (1);
            assert (context != null);
            
            SocketBase socket = ZMQ.zmq_socket (context, ZMQ.ZMQ_PULL);
            assert (socket != null);
            
            int val = 0;
            ZMQ.zmq_setsockopt (socket, ZMQ.ZMQ_LINGER, val);
            
            boolean rc = ZMQ.zmq_bind (socket, "ipc:///tmp/recon");
            assert (rc);

            Msg msg = ZMQ.zmq_recv (socket, 0);
            assertNotNull (msg);
            // Intentionally bail out
            ZMQ.zmq_close (socket);

            ZMQ.zmq_term (context);

            try {
                Thread.sleep (200);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            context = ZMQ.zmq_init (1);
            assert (context != null);

            socket = ZMQ.zmq_socket (context, ZMQ.ZMQ_PULL);
            assert (socket != null);

            val = 0;
            ZMQ.zmq_setsockopt(socket, ZMQ.ZMQ_LINGER, val);

            rc = ZMQ.zmq_bind (socket, "ipc:///tmp/recon");
            assert (rc);

            try {
                Thread.sleep (200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            msg = ZMQ.zmq_recv (socket, ZMQ.ZMQ_DONTWAIT);
            assertTrue (msg != null);

            // Start closing the socket while the connecting process is underway.
            ZMQ.zmq_close (socket);

            ZMQ.zmq_term (context);

        }
    }
    
    private static class Worker extends Thread 
    {
        @Override
        public void run () 
        {
            Ctx context = ZMQ.zmq_init (1);
            assert (context != null);
            
            SocketBase socket = ZMQ.zmq_socket (context, ZMQ.ZMQ_PUSH);
            assert (socket != null);
            
            int val = 0;
            ZMQ.zmq_setsockopt (socket, ZMQ.ZMQ_LINGER, val);
            
            val = 1;
            ZMQ.zmq_setsockopt (socket, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);
            
            boolean rc = ZMQ.zmq_connect (socket, "ipc:///tmp/recon");
            assert (rc);

            int hadone = 0;
            // Not checking RC as some may be -1
            for (int i = 0; i < 6; i++) {
                try {
                    Thread.sleep (200);
                } catch (InterruptedException e) {
                }
                int sent = ZMQ.zmq_send (socket, "hi", ZMQ.ZMQ_DONTWAIT);
                if (sent != -1)
                    hadone ++;
            }

            System.out.println ("haddone " + hadone);
            assertTrue (hadone >= 2);
            assertTrue (hadone < 4);
            
            ZMQ.zmq_close (socket);

            ZMQ.zmq_term (context);

        }
    }

    @Test
    public void testConnectDelay () throws Exception
    {
        Ctx context = ZMQ.zmq_ctx_new ();
        assert (context != null);

        SocketBase to = ZMQ.zmq_socket (context, ZMQ.ZMQ_PULL);
        assert (to != null);
        
        int val = 0;
        ZMQ.zmq_setsockopt (to, ZMQ.ZMQ_LINGER, val);
        boolean rc = ZMQ.zmq_bind (to, "tcp://*:7555");
        assert (rc);

        // Create a socket pushing to two endpoints - only 1 message should arrive.
        SocketBase from = ZMQ.zmq_socket (context, ZMQ.ZMQ_PUSH);
        assert(from != null);

        val = 0;
        ZMQ.zmq_setsockopt (from, ZMQ.ZMQ_LINGER, val);
        rc = ZMQ.zmq_connect (from, "tcp://localhost:7556");
        assert (rc);
        rc = ZMQ.zmq_connect (from, "tcp://localhost:7555");
        assert (rc);
        
        for (int i = 0; i < 10; ++i)
        {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.zmq_send (from, message, 0);
            assert(sent >= 0);
        }

        Thread.sleep(1000);
        int seen = 0;
        for (int i = 0; i < 10; ++i)
        {
            Msg msg = ZMQ.zmq_recv (to, ZMQ.ZMQ_DONTWAIT);
            if (msg == null)
                break;
            seen++;
        }
        assertEquals (seen, 5);

        ZMQ.zmq_close (from);
        
        ZMQ.zmq_close (to);
        
        ZMQ.zmq_term (context);
        
        context = ZMQ.zmq_ctx_new ();
        
        System.out.println (" Rerunning with DELAY_ATTACH_ON_CONNECT\n");

        to = ZMQ.zmq_socket (context, ZMQ.ZMQ_PULL);
        assert (to != null);
        rc = ZMQ.zmq_bind (to, "tcp://*:7560");
        assert (rc);

        val = 0;
        ZMQ.zmq_setsockopt (to, ZMQ.ZMQ_LINGER, val);
        assert (rc);

        // Create a socket pushing to two endpoints - all messages should arrive.
        from = ZMQ.zmq_socket (context, ZMQ.ZMQ_PUSH);
        assert (from != null);

        val = 0;
        ZMQ.zmq_setsockopt (from, ZMQ.ZMQ_LINGER, val);

        val = 1;
        ZMQ.zmq_setsockopt (from, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);
        
        rc = ZMQ.zmq_connect (from, "tcp://localhost:7561");
        assert (rc);

        rc = ZMQ.zmq_connect (from, "tcp://localhost:7560");
        assert (rc);

        for (int i = 0; i < 10; ++i)
        {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.zmq_send (from, message, 0);
            assert (sent >= 0);
        }

        Thread.sleep (1000);

        seen = 0;
        for (int i = 0; i < 10; ++i)
        {
            Msg msg = ZMQ.zmq_recv (to, ZMQ.ZMQ_DONTWAIT);
            assert (msg != null);
        }
        
        ZMQ.zmq_close (from);
        
        ZMQ.zmq_close (to);
        
        ZMQ.zmq_term (context);

        System.out.println (" Running DELAY_ATTACH_ON_CONNECT with disconnect\n");

        Thread serv, work;

        serv = new Server ();
        serv.start ();

        work = new Worker (); 
        work.start ();
        
        serv.join ();
        work.join ();
    }
}
