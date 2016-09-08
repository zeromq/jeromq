package zmq;

import java.util.UUID;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestLastEndpoint
{
    static void bindAndVerify(SocketBase s, String endpoint)
    {
        boolean brc = ZMQ.bind(s, endpoint);
        assertThat(brc, is(true));

        String stest = (String) ZMQ.getSocketOptionExt(s, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(stest, is(endpoint));
    }

    @Test
    public void testLastEndpoint()
    {
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(sb, notNullValue());

        bindAndVerify(sb, "tcp://127.0.0.1:5560");
        bindAndVerify(sb, "tcp://127.0.0.1:5561");
        bindAndVerify(sb, "ipc:///tmp/testep" + UUID.randomUUID().toString());

        sb.close();
        ctx.terminate();
    }
}
