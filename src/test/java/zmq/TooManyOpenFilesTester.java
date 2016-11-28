package zmq;

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
            Ctx ctx = ZMQ.init(1);

            SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);

            ZMQ.bind(server, "tcp://localhost:" + port);

            Msg msg = ZMQ.recv(server, 0);

            Msg address = msg;

            poll(server);

            msg = ZMQ.recv(server, 0);
            Msg delimiter = msg;

            poll(server);

            msg = ZMQ.recv(server, 0);

            // only one echo message for this server

            ZMQ.send(server, address, ZMQ.ZMQ_SNDMORE);
            ZMQ.send(server, delimiter, ZMQ.ZMQ_SNDMORE);
            ZMQ.send(server, msg, 0);

            // Clean up.
            ZMQ.close(server);
            ZMQ.term(ctx);
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
            Ctx ctx = ZMQ.init(1);

            SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);

            ZMQ.setSocketOption(client, ZMQ.ZMQ_IDENTITY, "ID");
            ZMQ.connect(client, "tcp://localhost:" + port);

            ZMQ.send(client, "DATA", 0);

            inBetween(client);

            Msg reply = ZMQ.recv(client, 0);
            assertThat(reply, notNullValue());
            assertThat(new String(reply.data(), ZMQ.CHARSET), is("DATA"));

            // Clean up.
            ZMQ.close(client);
            ZMQ.term(ctx);

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
        int rc = zmq.ZMQ.poll(new PollItem[] { item }, REQUEST_TIMEOUT);
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
            int port = Utils.findOpenPort();

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
