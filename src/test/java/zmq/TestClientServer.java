package zmq;

import org.junit.Test;

import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestClientServer
{
    @Test
    public void testInproc()
    {
        System.out.println("Scenario 1");

        String address = "inproc://client-server";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // bind server
        boolean rc = ZMQ.bind(server, address);
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.connect(client, address);
        assertThat(rc, is(true));

        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        // Recv message on the server side
        msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int routingId = msg.getRoutingId();
        assertThat(routingId, not(0));

        // Send message back to client using routing id
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(routingId);
        size = ZMQ.send(server, msg, 0);
        assertThat(size, is(5));

        // Client recv message from server
        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("HELLO"));

        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }

    @Test
    public void testTcp() throws Exception
    {
        System.out.println("Scenario 2");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // bind server
        boolean rc = ZMQ.bind(server, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.connect(client, "tcp://localhost:" + port);
        assertThat(rc, is(true));

        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        // Recv message on the server side
        msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int routingId = msg.getRoutingId();
        assertThat(routingId, not(0));

        // Send message back to client using routing id
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(routingId);
        size = ZMQ.send(server, msg, 0);
        assertThat(size, is(5));

        // Client recv message from server
        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("HELLO"));

        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }

    //  Client threads loop on send/recv until told to exit
    static class ClientThread extends Thread
    {
        final SocketBase client;

        ClientThread(SocketBase client)
        {
            this.client = client;
        }

        public void run()
        {
            for (int count = 0; count < 15000; count++) {
                Msg msg = new Msg("0".getBytes());
                int rc = ZMQ.send(client, msg, 0);
                assertThat(rc, is(1));
            }
            Msg msg = new Msg("1".getBytes());
            int rc = ZMQ.send(client, msg, 0);
            assertThat(rc, is(1));
        }
    }

    @Test
    public void testThreadSafe() throws Exception
    {
        System.out.println("Scenario 3");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());
        boolean rc = ZMQ.bind(server, "tcp://*:" + port);
        assertThat(rc, is(true));

        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.connect(client, "tcp://localhost:" + port);
        assertThat(rc, is(true));

        ClientThread t1 = new ClientThread(client);
        ClientThread t2 = new ClientThread(client);
        t1.start();
        t2.start();

        int threadsCompleted = 0;
        while (threadsCompleted < 2) {
            Msg msg = ZMQ.recv(server, 0);
            assertThat(msg, notNullValue());

            if (msg.data()[0] == '1') {
                threadsCompleted++; //  Thread ended
            }
        }

        t1.join();
        t2.join();

        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }

    @Test
    public void testAsRouterType() throws Exception
    {
        System.out.println("Scenario 2");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        //  Make wire type of the socket as ROUTER
        boolean rc = server.setSocketOpt(ZMQ.ZMQ_AS_TYPE, ZMQ.ZMQ_ROUTER);
        assertThat(rc, is(true));

        // bind server
        rc = ZMQ.bind(server, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a dealer
        SocketBase dealer = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());
        rc = ZMQ.connect(dealer, "tcp://localhost:" + port);
        assertThat(rc, is(true));

        // Send a message from dealer
        Msg msg = new Msg("X".getBytes());
        int size = ZMQ.send(dealer, msg, 0);
        assertThat(size, is(1));

        // Recv message on the server side
        msg = ZMQ.recv(server, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int routingId = msg.getRoutingId();
        assertThat(routingId, not(0));

        // Send message back to dealer using routing id
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(routingId);
        size = ZMQ.send(server, msg, 0);
        assertThat(size, is(5));

        // Dealer recv message from server
        msg = ZMQ.recv(dealer, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("HELLO"));

        ZMQ.close(dealer);
        ZMQ.close(server);
        ZMQ.term(context);
    }
}
