package zmq;

import org.junit.Test;
import zmq.util.Utils;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestPeer
{
    @Test
    public void testInproc()
    {
        System.out.println("Scenario 1");

        String address = "inproc://peer-to-peer";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a bind
        SocketBase bind = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(bind, notNullValue());

        // bind bind
        boolean rc = ZMQ.bind(bind, address);
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(client, notNullValue());
        int serverRoutingId = ZMQ.connectPeer(client, address);
        assertThat(serverRoutingId, not(0));

        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        msg.setRoutingId(serverRoutingId);
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        // Recv message on the bind side
        msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int clientRoutingId = msg.getRoutingId();
        assertThat(serverRoutingId, not(0));

        // Send message back to client using routing id
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(clientRoutingId);
        size = ZMQ.send(bind, msg, 0);
        assertThat(size, is(5));

        // Client recv message from bind
        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("HELLO"));

        ZMQ.close(client);
        ZMQ.close(bind);
        ZMQ.term(context);
    }

    @Test
    public void testTcp() throws Exception
    {
        System.out.println("Scenario 2");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a bind
        SocketBase bind = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(bind, notNullValue());

        // bind bind
        boolean rc = ZMQ.bind(bind, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(client, notNullValue());
        int serverRoutingId = ZMQ.connectPeer(client, "tcp://localhost:" + port);
        assertThat(serverRoutingId, not(0));
        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        msg.setRoutingId(serverRoutingId);
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        // Recv message on the bind side
        msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int clientRoutingId = msg.getRoutingId();
        assertThat(serverRoutingId, not(0));

        // Send message back to client using routing id
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(clientRoutingId);
        size = ZMQ.send(bind, msg, 0);
        assertThat(size, is(5));

        // Client recv message from bind
        msg = ZMQ.recv(client, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("HELLO"));

        ZMQ.close(client);
        ZMQ.close(bind);
        ZMQ.term(context);
    }

    @Test
    public void testTcpDisconnect() throws Exception
    {
        System.out.println("Scenario 3");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a bind
        SocketBase bind = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(bind, notNullValue());

        // bind bind
        boolean rc = ZMQ.bind(bind, "tcp://*:" + port);
        assertThat(rc, is(true));

        // create a client
        SocketBase client = ZMQ.socket(context, ZMQ.ZMQ_PEER);
        assertThat(client, notNullValue());
        int serverRoutingId = ZMQ.connectPeer(client, "tcp://localhost:" + port);
        assertThat(serverRoutingId, not(0));
        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        msg.setRoutingId(serverRoutingId);
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        // Recv message on the bind side
        msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data()), is("X"));
        int clientRoutingId = msg.getRoutingId();
        assertThat(serverRoutingId, not(0));

        boolean disconnectResult = ZMQ.disconnectPeer(bind, clientRoutingId);
        assertThat(disconnectResult, is(true));

        // Send message back to client fails after disconnect
        msg = new Msg("HELLO".getBytes());
        msg.setRoutingId(clientRoutingId);
        size = ZMQ.send(bind, msg, 0);
        assertThat(size, is(-1));

        ZMQ.close(client);
        ZMQ.close(bind);
        ZMQ.term(context);
    }
}
