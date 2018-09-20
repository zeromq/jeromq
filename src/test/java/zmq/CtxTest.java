package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import zmq.socket.Sockets;

public class CtxTest
{
    @Test
    public void testSeveralPendingInprocSocketsAreClosedIssue595()
    {
        Ctx ctx = ZMQ.init(0);
        assertThat(ctx, notNullValue());

        List<SocketBase> sockets = new ArrayList<>();
        for (Sockets type : Sockets.values()) {
            for (int idx = 0; idx < 3; ++idx) {
                SocketBase socket = ZMQ.socket(ctx, type.ordinal());
                assertThat(socket, notNullValue());

                boolean rc = socket.connect("inproc://" + type.name());
                assertThat(rc, is(true));
                sockets.add(socket);
            }
        }
        sockets.stream().forEach(ZMQ::close);
        ZMQ.term(ctx);

        assertThat(ctx.checkTag(), is(false));
    }
}
