package zmq.pipe;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class ConflateTest
{
    @Test
    public void test() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        SocketBase in = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assert (in != null);

        int port = Utils.findOpenPort();
        String host = "tcp://localhost:" + port;
        int conflate = 1;

        ZMQ.setSocketOption(in, ZMQ.ZMQ_CONFLATE, conflate);

        boolean rc = ZMQ.bind(in, host);
        assert (rc);

        SocketBase out = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assert (out != null);

        rc = ZMQ.connect(out, host);
        assert (rc);

        int messageCount = 20;
        for (int j = 0; j < messageCount; ++j) {
            int count = Helper.send(out, Integer.toString(j));
            assert (count > 0);
        }
        Thread.sleep(200);

        String recvd = Helper.recv(in);
        Assert.assertEquals("19", recvd);

        ZMQ.close(in);
        ZMQ.close(out);

        ZMQ.term(ctx);
    }
}
