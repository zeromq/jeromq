package zmq;

import org.junit.Test;
import zmq.util.Utils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestSocketPoll
{
    @Test
    public void testPollIn() throws Exception
    {
        System.out.println("Scenario 1");

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

        //  poll no message
        int ready = server.poll(ZMQ.ZMQ_POLLIN, 10, null);
        assertThat(ready, is(-1));
        assertThat(server.errno(), is(ZError.EAGAIN));

        // Send a message from client
        Msg msg = new Msg("X".getBytes());
        int size = ZMQ.send(client, msg, 0);
        assertThat(size, is(1));

        ready = server.poll(ZMQ.ZMQ_POLLIN, 10, null);
        assertThat(ready, is(ZMQ.ZMQ_POLLIN));

        ZMQ.close(client);
        ZMQ.close(server);
        ZMQ.term(context);
    }

    @Test
    public void cancelPoll() throws Exception
    {
        System.out.println("Scenario 2");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        AtomicBoolean cancellationToken = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            server.cancel(cancellationToken);
        });
        t.start();

        //  poll canceled
        int ready = server.poll(ZMQ.ZMQ_POLLIN, -1, cancellationToken);
        assertThat(ready, is(-1));
        assertThat(server.errno(), is(ZError.ECANCELED));

        ZMQ.close(server);
        ZMQ.term(context);
    }

    @Test
    public void testZeroTimeout() throws Exception
    {
        System.out.println("Scenario 3");

        int port = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create a server
        SocketBase server = ZMQ.socket(context, ZMQ.ZMQ_SERVER);
        assertThat(server, notNullValue());

        // bind server
        boolean rc = ZMQ.bind(server, "tcp://*:" + port);
        assertThat(rc, is(true));

        //  poll zero timeout
        int ready = server.poll(ZMQ.ZMQ_POLLIN, 0, null);
        assertThat(ready, is(-1));
        assertThat(server.errno(), is(ZError.EAGAIN));

        ZMQ.close(server);
        ZMQ.term(context);
    }
}
