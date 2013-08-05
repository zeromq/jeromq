/*
    Copyright (c) 2007-2013 Other contributors as noted in the AUTHORS file
                
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

public class TestConnectDelay
{

    @Test
    public void testConnectDelay1() throws Exception
    {
        // TEST 1.
        // First we're going to attempt to send messages to two
        // pipes, one connected, the other not. We should see
        // the PUSH load balancing to both pipes, and hence half
        // of the messages getting queued, as connect() creates a
        // pipe immediately.

        Ctx context = ZMQ.zmq_ctx_new();
        assert (context != null);

        SocketBase to = ZMQ.zmq_socket(context, ZMQ.ZMQ_PULL);
        assert (to != null);

        int val = 0;
        ZMQ.zmq_setsockopt(to, ZMQ.ZMQ_LINGER, val);
        boolean rc = ZMQ.zmq_bind(to, "tcp://*:7555");
        assert (rc);

        // Create a socket pushing to two endpoints - only 1 message should arrive.
        SocketBase from = ZMQ.zmq_socket(context, ZMQ.ZMQ_PUSH);
        assert (from != null);

        val = 0;
        ZMQ.zmq_setsockopt(from, ZMQ.ZMQ_LINGER, val);
        rc = ZMQ.zmq_connect(from, "tcp://localhost:7556");
        assert (rc);
        rc = ZMQ.zmq_connect(from, "tcp://localhost:7555");
        assert (rc);

        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.zmq_send(from, message, 0);
            assert (sent >= 0);
        }

        // We now consume from the connected pipe
        // - we should see just 5
        int timeout = 1000;
        ZMQ.zmq_setsockopt(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.zmq_recv(to, 0);
            if (msg == null)
                break;
            seen++;
        }
        assertEquals(seen, 5);

        ZMQ.zmq_close(from);

        ZMQ.zmq_close(to);

        ZMQ.zmq_term(context);
    }

    @Test
    public void testConnectDelay2() throws Exception
    {
        // TEST 2
        // This time we will do the same thing, connect two pipes,
        // one of which will succeed in connecting to a bound
        // receiver, the other of which will fail. However, we will
        // also set the delay attach on connect flag, which should
        // cause the pipe attachment to be delayed until the connection
        // succeeds.
        Ctx context = ZMQ.zmq_ctx_new();

        SocketBase to = ZMQ.zmq_socket(context, ZMQ.ZMQ_PULL);
        assert (to != null);
        boolean rc = ZMQ.zmq_bind(to, "tcp://*:7560");
        assert (rc);

        int val = 0;
        ZMQ.zmq_setsockopt(to, ZMQ.ZMQ_LINGER, val);
        assert (rc);

        // Create a socket pushing to two endpoints - all messages should arrive.
        SocketBase from = ZMQ.zmq_socket(context, ZMQ.ZMQ_PUSH);
        assert (from != null);

        val = 0;
        ZMQ.zmq_setsockopt(from, ZMQ.ZMQ_LINGER, val);

        // Set the key flag
        val = 1;
        ZMQ.zmq_setsockopt(from, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);

        // Connect to the invalid socket
        rc = ZMQ.zmq_connect(from, "tcp://localhost:7561");
        assert (rc);
        // Connect to the valid socket
        rc = ZMQ.zmq_connect(from, "tcp://localhost:7560");
        assert (rc);

        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.zmq_send(from, message, 0);
            assert (sent >= 0);
        }

        int timeout = 1000;
        ZMQ.zmq_setsockopt(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.zmq_recv(to, 0);
            if (msg == null)
                break;
            seen++;
        }
        assertEquals(seen, 10);

        ZMQ.zmq_close(from);

        ZMQ.zmq_close(to);

        ZMQ.zmq_term(context);
    }

    @Test
    public void testConnectDelay3() throws Exception
    {
        // TEST 3
        // This time we want to validate that the same blocking behaviour
        // occurs with an existing connection that is broken. We will send
        // messages to a connected pipe, disconnect and verify the messages
        // block. Then we reconnect and verify messages flow again.
        Ctx context = ZMQ.zmq_ctx_new();

        SocketBase backend = ZMQ.zmq_socket(context, ZMQ.ZMQ_DEALER);
        assert (backend != null);

        SocketBase frontend = ZMQ.zmq_socket(context, ZMQ.ZMQ_DEALER);
        assert (frontend != null);

        int val = 0;
        ZMQ.zmq_setsockopt(backend, ZMQ.ZMQ_LINGER, val);

        val = 0;
        ZMQ.zmq_setsockopt(frontend, ZMQ.ZMQ_LINGER, val);

        //  Frontend connects to backend using DELAY_ATTACH_ON_CONNECT
        val = 1;
        ZMQ.zmq_setsockopt(frontend, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);

        boolean rc = ZMQ.zmq_bind(backend, "tcp://*:7760");
        assert (rc);

        rc = ZMQ.zmq_connect(frontend, "tcp://localhost:7760");
        assert (rc);

        //  Ping backend to frontend so we know when the connection is up
        int sent = ZMQ.zmq_send(backend, "Hello", 0);
        assertEquals(5, sent);

        Msg msg = ZMQ.zmq_recv(frontend, 0);
        assertEquals(5, msg.size());

        // Send message from frontend to backend
        sent = ZMQ.zmq_send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(5, sent);

        ZMQ.zmq_close(backend);

        //  Give time to process disconnect
        //  There's no way to do this except with a sleep
        Thread.sleep(1000);

        // Send a message, should fail
        sent = ZMQ.zmq_send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(-1, sent);

        //  Recreate backend socket
        backend = ZMQ.zmq_socket(context, ZMQ.ZMQ_DEALER);
        val = 0;
        ZMQ.zmq_setsockopt(backend, ZMQ.ZMQ_LINGER, val);
        rc = ZMQ.zmq_bind(backend, "tcp://*:7760");
        assert (rc);

        //  Ping backend to frontend so we know when the connection is up
        sent = ZMQ.zmq_send(backend, "Hello", 0);
        assertEquals(5, sent);

        msg = ZMQ.zmq_recv(frontend, 0);
        assertEquals(5, msg.size());

        // After the reconnect, should succeed
        sent = ZMQ.zmq_send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(5, sent);

        ZMQ.zmq_close(backend);

        ZMQ.zmq_close(frontend);

        ZMQ.zmq_term(context);
    }
}
