package zmq;

import org.junit.Test;
import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestHiccupMsg
{
    @Test
    public void test() throws Exception
    {
        int port = Utils.findOpenPort();
        String address = "tcp://localhost:" + port;

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // bind server
        boolean rc = ZMQ.bind(server, address);
        assertThat(rc, is(true));

        // Create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_CLIENT);
        assertThat(client, notNullValue());
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HELLO_MSG, "HELLO");
        assertThat(rc, is(true));
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HICCUP_MSG, "HICCUP");
        assertThat(rc, is(true));
        rc = ZMQ.connect(client, address);
        assertThat(rc, is(true));

        // Receive the hello message from client
        Msg msg = ZMQ.recv(server, 0);
        assertThat(new String(msg.data()), is("HELLO"));

        // Kill the server
        ZMQ.close(server);

        // Receive the hiccup message
        msg = ZMQ.recv(client, 0);
        assertThat(new String(msg.data()), is("HICCUP"));

        //  Clean up.
        ZMQ.close(client);
        ZMQ.term(context);
    }
}
