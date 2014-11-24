package zmq;

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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

/**
 * Tests exhaustion of java file pipes,
 * each component being on a separate thread.
 * @author fred
 *
 */
public class TooManyOpenFilesTester
{
    private static final long REQUEST_TIMEOUT = 1000; // msecs

    /**
     * A simple server for one reply only.
     * @author fred
     *
     */
    private class Server extends Thread
    {
        private final int port;

        /**
         * Creates a new server.
         * @param port the port to which to connect.
         */
        public Server(int port)
        {
            this.port = port;
        }

        @Override
        public void run()
        {
            Ctx ctx = ZMQ.zmqInit(1);

            SocketBase server = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_ROUTER);

            ZMQ.zmq_bind(server, "tcp://localhost:" + port);

            Msg msg = ZMQ.zmq_recv(server, 0);

            Msg address = msg;

            poll(server);

            msg = ZMQ.zmq_recv(server, 0);
            Msg delimiter = msg;

            poll(server);

            msg = ZMQ.zmq_recv(server, 0);

            // only one echo message for this server

            ZMQ.zmq_send(server, address, ZMQ.ZMQ_SNDMORE);
            ZMQ.zmq_send(server, delimiter, ZMQ.ZMQ_SNDMORE);
            ZMQ.zmq_send(server, msg, 0);

            // Clean up.
            ZMQ.zmq_close(server);
            ZMQ.zmq_term(ctx);
        }
    }

    /**
     * Simple client.
     * @author fred
     *
     */
    private class Client extends Thread
    {
        private final int port;

        final AtomicBoolean finished = new AtomicBoolean();

        /**
         * Creates a new client.
         * @param port the port to which to connect.
         */
        public Client(int port)
        {
            this.port = port;
        }

        @Override
        public void run()
        {
            Ctx ctx = ZMQ.zmqInit(1);

            SocketBase client = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REQ);

            ZMQ.zmq_setsockopt(client, ZMQ.ZMQ_IDENTITY, "ID");
            ZMQ.zmq_connect(client, "tcp://localhost:" + port);

            ZMQ.zmq_send(client, "DATA", 0);

            inBetween(client);

            Msg reply = ZMQ.zmq_recv(client, 0);
            assertThat(reply, notNullValue());
            assertThat(new String(reply.data(), ZMQ.CHARSET), is("DATA"));

            // Clean up.
            ZMQ.zmq_close(client);
            ZMQ.zmq_term(ctx);

            finished.set(true);
        }

        /**
         * Called between the request-reply cycle.
         * @param client the socket participating to the cycle of request-reply
         */
        protected void inBetween(SocketBase client)
        {
            poll(client);
        }
    }

    /**
     * Polls while keeping the selector opened.
     * @param socket the socket to poll
     */
    private void poll(SocketBase socket)
    {
        // Poll socket for a reply, with timeout
        PollItem item = new PollItem(socket, ZMQ.ZMQ_POLLIN);
        int rc = zmq.ZMQ.zmq_poll(new PollItem[] { item }, REQUEST_TIMEOUT);
        assertThat(rc, is(1));

        boolean readable = item.isReadable();
        assertThat(readable, is(true));
    }

    /**
     * Test exhaustion of java pipes.
     * Exhaustion can currently come from {@link zmq.Signaler} that are not closed
     * or from {@link Selector} that are not closed.
     * @throws Exception if something bad occurs.
     */
    @Test
    public void testReqRouterTcpPoll() throws Exception
    {
        // we have no direct way to test this, except by running a bunch of tests and waiting for the failure to happen...
        // crashed on iteration 3000-ish in my machine for poll selectors; on iteration 16-ish for sockets
        for (int index = 0; index < 10000; ++index) {
            long start = System.currentTimeMillis();
            List<Pair> pairs = new ArrayList<Pair>();
            int port = 5963;

            for (int idx = 0; idx < 20; ++idx) {
                Pair pair = testWithPoll(port + idx);
                pairs.add(pair);
            }

            for (Pair p : pairs) {
                p.server.join();
                p.client.join();
            }

            boolean finished = true;
            for (Pair p : pairs) {
                finished &= p.client.finished.get();
            }
            long end = System.currentTimeMillis();
            assertThat(finished, is(true));

            System.out.printf(
                    "Test %s finished in %s millis.\n",
                    index, (end - start));
        }
    }

    /**
     * Dummy class to help keep relation between client and server.
     * @author fred
     *
     */
    private class Pair
    {
        private Client client;
        private Server server;
    }

    private Pair testWithPoll(int port)
    {
        Server server = new Server(port);

        server.start();

        Client client = new Client(port);
        client.start();

        Pair pair = new Pair();
        pair.server = server;
        pair.client = client;
        return pair;
    }
}
