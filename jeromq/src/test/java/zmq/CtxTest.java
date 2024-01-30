package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
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
        for (SocketBase socket : sockets) {
            ZMQ.close(socket);
        }
        ZMQ.term(ctx);

        assertThat(ctx.checkTag(), is(false));
    }

    @Test
    public void testSetHandler()
{
        Ctx ctx = ZMQ.init(0);
        SocketBase socket = ZMQ.socket(ctx, Sockets.CLIENT.ordinal());
        Assert.assertThrows(IllegalStateException.class, () -> ctx.setNotificationExceptionHandler(null));
        Assert.assertThrows(IllegalStateException.class, () -> ctx.setUncaughtExceptionHandler(null));
        ZMQ.close(socket);
    }
}
