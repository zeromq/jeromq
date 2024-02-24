package zmq.pipe;

import org.junit.Assert;
import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;

public class ConflateTest
{
    @Test
    public void test() throws InterruptedException
    {
        Ctx ctx = ZMQ.init(1);
        assert (ctx != null);

        SocketBase in = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assert (in != null);

        String host = "tcp://localhost:*";
        int conflate = 1;

        ZMQ.setSocketOption(in, ZMQ.ZMQ_CONFLATE, conflate);

        boolean rc = ZMQ.bind(in, host);
        assert (rc);

        SocketBase out = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assert (out != null);

        String ep = (String) ZMQ.getSocketOptionExt(in, ZMQ.ZMQ_LAST_ENDPOINT);
        rc = ZMQ.connect(out, ep);
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
