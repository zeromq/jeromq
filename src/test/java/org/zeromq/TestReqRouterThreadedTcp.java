package org.zeromq;
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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.zeromq.ZMQ.Socket;

/**
 * Tests a REQ-ROUTER dialog with several methods,
 * each component being on a separate thread.
 * @author fred
 *
 */
public class TestReqRouterThreadedTcp
{
    private static final long REQUEST_TIMEOUT = 1000; // msecs

    /**
     * A very simple server for one reply only.
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
            ZContext ctx = new ZContext();

            ZMQ.Socket server = ctx.createSocket(ZMQ.ROUTER);
            server.bind("tcp://localhost:" + port);

            ZMsg msg = ZMsg.recvMsg(server);
            // only one echo message for this server
            msg.send(server);

            msg.destroy();

            // Clean up.
            ctx.destroySocket(server);
            ctx.close();
        }
    }

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
            ZContext ctx = new ZContext();

            ZMQ.Socket client = ctx.createSocket(ZMQ.REQ);

            client.connect("tcp://localhost:" + port);

            client.send("DATA");

            inBetween(client);

            String reply = client.recvStr();
            assertThat(reply, notNullValue());
            assertThat(reply, is("DATA"));

            // Clean up.
            ctx.destroySocket(client);
            ctx.close();

            finished.set(true);
        }

        /**
         * Called between the request-reply cycle.
         * @param client the socket participating to the cycle of request-reply
         */
        protected void inBetween(Socket client)
        {
            // to be overriden
        }
    }

    private class ClientPoll extends Client
    {
        public ClientPoll(int port)
        {
            super(port);
        }

        // same results
//        @Override
//        protected void inBetween(Socket client) {
//            // Poll socket for a reply, with timeout
//            PollItem items[] = { new PollItem(client, ZMQ.Poller.POLLIN) };
//            int rc = ZMQ.poll(items, 1, REQUEST_TIMEOUT);
//            assertThat(rc, is(1));
//            boolean readable = items[0].isReadable();
//              assertThat(readable, is(true));
//        }

        /**
         * Here we use a poller to check for readability of the message.
         * This should activate the prefetching mechanism.
         */
        @Override
        protected void inBetween(Socket client)
        {
            // Poll socket for a reply, with timeout
            ZMQ.Poller poller = new ZMQ.Poller(1);
            poller.register(client, ZMQ.Poller.POLLIN);

            int rc = poller.poll(REQUEST_TIMEOUT);
            assertThat(rc, is(1));

            boolean readable = poller.pollin(0);
            assertThat(readable, is(true));
            // now a message should have been prefetched
        }
    }

    /**
     * Test dialog directly.
     * @throws Exception if something bad occurs.
     */
    @Test
    public void testReqRouterTcp() throws Exception
    {
        int port = 5962;
        Server server = new Server(port);

        server.start();

        Client client = new Client(port);
        client.start();

        server.join();
        client.join();

        boolean finished = client.finished.get();
        assertThat(finished, is(true));
    }

    /**
     * Test dialog with a polling access in between request-reply.
     * This should activate the prefetching mechanism.
     * @throws Exception if something bad occurs.
     */
    @Test
    public void testReqRouterTcpPoll() throws Exception
    {
        int port = 5963;
        Server server = new Server(port);

        server.start();

        ClientPoll client = new ClientPoll(port);
        client.start();

        server.join();
        client.join();

        boolean finished = client.finished.get();
        assertThat(finished, is(true));
    }
}
