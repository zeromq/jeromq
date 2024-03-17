package zmq;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestChannel
{
    @Test
    public void testRoundtrip()
    {
        String address = "inproc://channel";

        Ctx context = ZMQ.createContext();
        assertThat(context, notNullValue());

        // Create bind channel
        SocketBase bChannel = ZMQ.socket(context, ZMQ.ZMQ_CHANNEL);
        assertThat(bChannel, notNullValue());
        boolean rc = ZMQ.bind(bChannel, address);
        assertThat(rc, is(true));

        // Create connect channel
        SocketBase cChannel = ZMQ.socket(context, ZMQ.ZMQ_CHANNEL);
        assertThat(cChannel, notNullValue());
        rc = ZMQ.connect(cChannel, address);
        assertThat(rc, is(true));

        int sent = ZMQ.send(cChannel, "HELLO", 0);
        assertThat(sent, is(5));

        Msg msg = ZMQ.recv(bChannel, 0);
        assertThat(new String(msg.data()), is("HELLO"));

        sent = ZMQ.send(bChannel, "WORLD", 0);
        assertThat(sent, is(5));

        msg = ZMQ.recv(cChannel, 0);
        assertThat(new String(msg.data()), is("WORLD"));

        ZMQ.close(cChannel);
        ZMQ.close(bChannel);
        ZMQ.term(context);
    }
}
