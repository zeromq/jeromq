package zmq.socket.pair;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;

public class TestPairIpc
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testPairIpc()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase pairBind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairBind, notNullValue());

        UUID random = UUID.randomUUID();

        boolean brc = ZMQ.bind(pairBind, "ipc:///tmp/tester/" + random.toString());
        assertThat(brc, is(true));

        SocketBase pairConnect = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairConnect, notNullValue());

        brc = ZMQ.connect(pairConnect, "ipc:///tmp/tester/" + random.toString());
        assertThat(brc, is(true));

        Helper.bounce(pairBind, pairConnect);

        //  Tear down the wiring.
        ZMQ.close(pairBind);
        ZMQ.close(pairConnect);
        ZMQ.term(ctx);
    }
}
