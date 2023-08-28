package zmq;

import org.junit.Test;

import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestHelloMsg
{
    @Test
    public void testTcp() throws Exception
    {
        System.out.println("Scenario 1");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a routergit s
        SocketBase router = ZMQ.socket(context, ZMQ.ZMQ_ROUTER);
        assertThat(router, notNullValue());

        // set router socket options
        boolean rc = ZMQ.setSocketOption(router, ZMQ.ZMQ_HELLO_MSG, "H");
        assertThat(rc, is(true));

        // bind router
        rc = ZMQ.bind(router, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a dealer
        SocketBase dealer = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());
        rc = ZMQ.connect(dealer, "tcp://localhost:" + port);
        assertThat(rc, is(true));

        // receive hello message from router
        Msg msg = ZMQ.recv(dealer, 0);
        assertThat(new String(msg.data()), is("H"));

        ZMQ.close(dealer);
        ZMQ.close(router);
        ZMQ.term(context);
    }

    @Test
    public void testInproc()
    {
        System.out.println("Scenario 2");

        String address = "inproc://hello-msg";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a router
        SocketBase router = ZMQ.socket(context, ZMQ.ZMQ_ROUTER);
        assertThat(router, notNullValue());

        // set router socket options
        boolean rc = ZMQ.setSocketOption(router, ZMQ.ZMQ_HELLO_MSG, "H");
        assertThat(rc, is(true));

        // bind router
        rc = ZMQ.bind(router, address);
        assertThat(rc, is(true));

        // create a dealer
        SocketBase dealer = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());
        rc = ZMQ.connect(dealer, address);
        assertThat(rc, is(true));

        // receive hello message from router
        Msg msg = ZMQ.recv(dealer, 0);
        assertThat(new String(msg.data()), is("H"));

        ZMQ.close(dealer);
        ZMQ.close(router);
        ZMQ.term(context);
    }

    @Test
    public void testInprocLateBind()
    {
        System.out.println("Scenario 3");

        String address = "inproc://hello-msg";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assertThat(server, notNullValue());

        // set server socket options
        boolean rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_HELLO_MSG, "W");
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HELLO_MSG, "H");
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, address);
        assertThat(rc, is(true));

        // bind server after the client
        rc = ZMQ.bind(server, address);
        assertThat(rc, is(true));

        // Receive the welcome message from server
        Msg msg = ZMQ.recv(client, 0);
        assertThat(new String(msg.data()), is("W"));

        // Receive the hello message from client
        msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("H"));

        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }
}
