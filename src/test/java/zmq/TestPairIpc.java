package zmq;

import java.util.UUID;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestPairIpc
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testPairIpc()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(sb, notNullValue());

        UUID random = UUID.randomUUID();

        boolean brc = ZMQ.bind(sb, "ipc:///tmp/tester" + random.toString());
        assertThat(brc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(sc, notNullValue());

        brc = ZMQ.connect(sc, "ipc:///tmp/tester" + random.toString());
        assertThat(brc, is(true));

        Helper.bounce(sb, sc);

        //  Tear down the wiring.
        ZMQ.close(sb);
        ZMQ.close(sc);
        ZMQ.term(ctx);
    }
}
