package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
 * Tests exhaustion of java file pipes,
 * each component being on a separate thread.
 *
 */
@Ignore
public class TooManyOpenFilesTester
{
    private static final long REQUEST_TIMEOUT = 2000; // msecs

    /**
     * A simple server for one reply only.
     *
     */
    private class Server extends Thread
    {
        private final int port;

        /**
         * Creates a new server.
         * @param port the port to which to connect.
         * @param idx the index of the server
         */
        public Server(int port, int idx)
        {
            this.port = port;
            setName("Server-" + idx);
        }

        @Override
        public void run()
        {
            ZContext ctx = new ZContext(1);

            Socket server = ctx.createSocket(SocketType.ROUTER);

            server.bind("tcp://localhost:" + port);

            byte[] msg = server.recv(0);

            byte[] address = msg;

            poll(ctx, server);

            msg = server.recv(0);
            byte[] delimiter = msg;

            poll(ctx, server);

            msg = server.recv(0);

            // only one echo message for this server

            server.send(address, ZMQ.SNDMORE);
            server.send(delimiter, ZMQ.SNDMORE);
            server.send(msg, 0);

            // Clean up.
            ctx.destroy();
        }
    }

    /**
     * Simple client.
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
        public Client(int port, int idx)
        {
            this.port = port;
            setName("Client-" + idx);
        }

        @Override
        public void run()
        {
            ZContext ctx = new ZContext(1);

            Socket client = ctx.createSocket(SocketType.REQ);

            client.setIdentity("ID".getBytes());
            client.connect("tcp://localhost:" + port);

            client.send("DATA", 0);

            inBetween(ctx, client);

            byte[] reply = client.recv(0);
            assertThat(reply, notNullValue());
            assertThat(new String(reply, ZMQ.CHARSET), is("DATA"));

            // Clean up.
            ctx.destroy();

            finished.set(true);
        }

        /**
         * Called between the request-reply cycle.
         * @param client the socket participating to the cycle of request-reply
         * @param selector the selector used for polling
         */
        protected void inBetween(ZContext ctx, Socket client)
        {
            poll(ctx, client);
        }
    }

    /**
     * Polls while keeping the selector opened.
     * @param socket the socket to poll
     * @param selector the selector used for polling
     */
    private void poll(ZContext ctx, Socket socket)
    {
        // Poll socket for a reply, with timeout
        Poller poller = ctx.createPoller(1);
        poller.register(socket, Poller.POLLIN);
        int rc = poller.poll(REQUEST_TIMEOUT);
        assertThat(rc, is(1));

        boolean readable = poller.pollin(0);
        assertThat(readable, is(true));

        poller.close();
    }

    /**
     * Test exhaustion of java pipes.
     * Exhaustion can currently come from zmq.Signaler that are not closed
     * or from {@link java.nio.channels.Selector} that are not closed.
     * @throws Exception if something bad occurs.
     */
    @Test
    public void testReqRouterTcpPoll() throws Exception
    {
        // we have no direct way to test this, except by running a bunch of tests and waiting for the failure to happen...
        // crashed on iteration 3000-ish in my machine for poll selectors; on iteration 16-ish for sockets
        for (int index = 0; index < 10000; ++index) {
            long start = System.currentTimeMillis();
            List<Pair> pairs = new ArrayList<>();

            for (int idx = 0; idx < 20; ++idx) {
                Pair pair = testWithPoll(idx);
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

            System.out.printf("Test %s finished in %s millis.\n", index, (end - start));
        }
    }

    /**
     * Dummy class to help keep relation between client and server.
     */
    private class Pair
    {
        private Client client;
        private Server server;
    }

    private Pair testWithPoll(int idx) throws IOException
    {
        int port = Utils.findOpenPort();

        Server server = new Server(port, idx);

        server.start();

        Client client = new Client(port, idx);
        client.start();

        Pair pair = new Pair();
        pair.server = server;
        pair.client = client;
        return pair;
    }
}
