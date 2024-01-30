package zmq;

import org.junit.Test;
import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestDisconnectMsg
{
    void test(String address)
    {
        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // set router socket options
        boolean rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_DISCONNECT_MSG, "D");
        assertThat(rc, is(true));

        // bind server
        rc = ZMQ.bind(server, address);
        assertThat(rc, is(true));

        // Create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HELLO_MSG, "H");
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, address);
        assertThat(rc, is(true));

        // Receive the hello message from client
        Msg msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("H"));

        // Kill the client
        ZMQ.close(client);

        // Receive the disconnect message
        msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("D"));

        //  Clean up.
        ZMQ.close(server);
        ZMQ.term(context);
    }

    @Test
    public void testTcp() throws Exception
    {
        System.out.println("Scenario 1");

        int port = Utils.findOpenPort();
        String address = "tcp://localhost:" + port;

        test(address);
    }

    @Test
    public void testInproc()
    {
        System.out.println("Scenario 2");

        test("inproc://disconnect-msg");
    }

    @Test
    public void testInprocDisconnect()
    {
        System.out.println("Scenario 3");

        String address = "inproc://disconnect-msg";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // set server socket options
        boolean rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_DISCONNECT_MSG, "D");
        assertThat(rc, is(true));

        // bind server
        rc = ZMQ.bind(server, address);
        assertThat(rc, is(true));

        // Create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HELLO_MSG, "H");
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, address);
        assertThat(rc, is(true));

        // Receive the hello message from client
        Msg msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("H"));

        // disconnect the client
        rc = client.termEndpoint(address);
        assertThat(rc, is(true));

        // Receive the disconnect message
        msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("D"));

        //  Clean up.
        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }
}
