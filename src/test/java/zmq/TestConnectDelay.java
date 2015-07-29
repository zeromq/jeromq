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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

        Ctx context = ZMQ.createContext();
        assert (context != null);

        SocketBase to = ZMQ.socket(context, ZMQ.ZMQ_PULL);
        assert (to != null);

        int val = 0;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_LINGER, val);
        boolean rc = ZMQ.bind(to, "tcp://*:7555");
        assert (rc);

        // Create a socket pushing to two endpoints - only 1 message should arrive.
        SocketBase from = ZMQ.socket(context, ZMQ.ZMQ_PUSH);
        assert (from != null);

        val = 0;
        ZMQ.setSocketOption(from, ZMQ.ZMQ_LINGER, val);
        rc = ZMQ.connect(from, "tcp://localhost:7556");
        assert (rc);
        rc = ZMQ.connect(from, "tcp://localhost:7555");
        assert (rc);

        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.send(from, message, 0);
            assert (sent >= 0);
        }

        // We now consume from the connected pipe
        // - we should see just 5
        int timeout = 1000;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.recv(to, 0);
            if (msg == null) {
                break;
            }
            seen++;
        }
        assertEquals(seen, 5);

        ZMQ.close(from);

        ZMQ.close(to);

        ZMQ.term(context);
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
        Ctx context = ZMQ.createContext();

        SocketBase to = ZMQ.socket(context, ZMQ.ZMQ_PULL);
        assert (to != null);
        boolean rc = ZMQ.bind(to, "tcp://*:7560");
        assert (rc);

        int val = 0;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_LINGER, val);
        assert (rc);

        // Create a socket pushing to two endpoints - all messages should arrive.
        SocketBase from = ZMQ.socket(context, ZMQ.ZMQ_PUSH);
        assert (from != null);

        val = 0;
        ZMQ.setSocketOption(from, ZMQ.ZMQ_LINGER, val);

        // Set the key flag
        val = 1;
        ZMQ.setSocketOption(from, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);

        // Connect to the invalid socket
        rc = ZMQ.connect(from, "tcp://localhost:7561");
        assert (rc);
        // Connect to the valid socket
        rc = ZMQ.connect(from, "tcp://localhost:7560");
        assert (rc);

        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.send(from, message, 0);
            assert (sent >= 0);
        }

        int timeout = 1000;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.recv(to, 0);
            if (msg == null) {
                break;
            }
            seen++;
        }
        assertEquals(seen, 10);

        ZMQ.close(from);

        ZMQ.close(to);

        ZMQ.term(context);
    }

    @Test
    public void testConnectDelay3() throws Exception
    {
        // TEST 3
        // This time we want to validate that the same blocking behaviour
        // occurs with an existing connection that is broken. We will send
        // messages to a connected pipe, disconnect and verify the messages
        // block. Then we reconnect and verify messages flow again.
        Ctx context = ZMQ.createContext();

        SocketBase backend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assert (backend != null);

        SocketBase frontend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assert (frontend != null);

        int val = 0;
        ZMQ.setSocketOption(backend, ZMQ.ZMQ_LINGER, val);

        val = 0;
        ZMQ.setSocketOption(frontend, ZMQ.ZMQ_LINGER, val);

        //  Frontend connects to backend using DELAY_ATTACH_ON_CONNECT
        val = 1;
        ZMQ.setSocketOption(frontend, ZMQ.ZMQ_DELAY_ATTACH_ON_CONNECT, val);

        boolean rc = ZMQ.bind(backend, "tcp://*:7760");
        assert (rc);

        rc = ZMQ.connect(frontend, "tcp://localhost:7760");
        assert (rc);

        //  Ping backend to frontend so we know when the connection is up
        int sent = ZMQ.send(backend, "Hello", 0);
        assertEquals(5, sent);

        Msg msg = ZMQ.recv(frontend, 0);
        assertEquals(5, msg.size());

        // Send message from frontend to backend
        sent = ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(5, sent);

        ZMQ.close(backend);

        //  Give time to process disconnect
        //  There's no way to do this except with a sleep
        Thread.sleep(1000);

        // Send a message, should fail
        sent = ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(-1, sent);

        //  Recreate backend socket
        backend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        val = 0;
        ZMQ.setSocketOption(backend, ZMQ.ZMQ_LINGER, val);
        rc = ZMQ.bind(backend, "tcp://*:7760");
        assert (rc);

        //  Ping backend to frontend so we know when the connection is up
        sent = ZMQ.send(backend, "Hello", 0);
        assertEquals(5, sent);

        msg = ZMQ.recv(frontend, 0);
        assertEquals(5, msg.size());

        // After the reconnect, should succeed
        sent = ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        assertEquals(5, sent);

        ZMQ.close(backend);

        ZMQ.close(frontend);

        ZMQ.term(context);
    }
}
