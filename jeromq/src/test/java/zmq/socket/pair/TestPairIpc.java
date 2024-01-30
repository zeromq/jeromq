package zmq.socket.pair;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;

public class TestPairIpc
{
    //  Create REQ/ROUTER wiring.

    @Test(timeout = 5000)
    public void testPairIpc() throws IOException
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase pairBind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairBind, notNullValue());

        Path temp = Files.createTempFile("zmq-test-", ".sock");
        Files.delete(temp);
        assertTrue(ZMQ.bind(pairBind, "ipc:///" + temp));

        SocketBase pairConnect = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairConnect, notNullValue());

        boolean brc = ZMQ.connect(pairConnect, "ipc:///" + temp);
        assertThat(brc, is(true));

        Helper.bounce(pairBind, pairConnect);

        //  Tear down the wiring.
        ZMQ.close(pairBind);
        ZMQ.close(pairConnect);
        ZMQ.term(ctx);
    }
}
