package zmq.socket.pair;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestPairIpc
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    //  Create REQ/ROUTER wiring.

    @Test(timeout = 5000)
    public void testPairIpc()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase pairBind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairBind, notNullValue());

        Path temp = tempFolder.getRoot().toPath().resolve("zmq-test.sock");
        assertTrue(ZMQ.bind(pairBind, "ipc://" + temp));

        SocketBase pairConnect = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        assertThat(pairConnect, notNullValue());

        boolean brc = ZMQ.connect(pairConnect, "ipc://" + temp);
        assertThat(brc, is(true));

        Helper.bounce(pairBind, pairConnect);

        //  Tear down the wiring.
        ZMQ.close(pairBind);
        ZMQ.close(pairConnect);
        ZMQ.term(ctx);
    }
}
