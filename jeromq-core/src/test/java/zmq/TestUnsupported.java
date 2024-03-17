package zmq;

import java.util.List;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestUnsupported
{
    Ctx ctx = ZMQ.init(1);

    @After
    public void close()
    {
        ctx.terminate();
    }

    private void doOperation(Predicate<SocketBase> networkOperation)
    {
        SocketBase socket = ZMQ.socket(ctx, ZMQ.ZMQ_SERVER);
        try {
            boolean rc = networkOperation.test(socket);
            assertThat(rc, is(false));
            assertThat(socket.errno.get(), is(ZError.EPROTONOSUPPORT));
        }
        finally {
            socket.close();
        }
    }

    @Test(timeout = 100)
    public void doTests()
    {
        for (String proto : List.of("pgm", "epgm", "norm", "ws", "wss", "tipc", "vmci")) {
            doOperation(s -> s.connect(proto + "://localhost:*"));
            doOperation(s -> s.bind(proto + "://localhost:*"));
        }
    }
}
