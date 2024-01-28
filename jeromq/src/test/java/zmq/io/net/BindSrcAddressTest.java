package zmq.io.net;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class BindSrcAddressTest
{
    @Test
    public void test() throws IOException
    {
        Ctx ctx = ZMQ.createContext();
        assert (ctx != null);

        SocketBase socket = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assert (socket != null);

        int port1 = Utils.findOpenPort();
        int port2 = Utils.findOpenPort();
        int port3 = Utils.findOpenPort();
        boolean rc = ZMQ.connect(socket, "tcp://127.0.0.1:0;localhost:" + port1);
        assert (rc);

        rc = ZMQ.connect(socket, "tcp://localhost:" + port3 + ";localhost:" + port2);
        assert (rc);

        ZMQ.close(socket);

        ZMQ.term(ctx);
    }
}
