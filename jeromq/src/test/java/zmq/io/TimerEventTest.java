package zmq.io;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.util.TestUtils;

public class TimerEventTest
{
    static class SocketMonitor extends Thread
    {
        private final Ctx                        ctx;
        private final String                     monitorAddr;
        private final AtomicReference<ZMQ.Event> event = new AtomicReference<>();

        public SocketMonitor(Ctx ctx, String monitorAddr)
        {
            this.ctx = ctx;
            this.monitorAddr = monitorAddr;
        }

        @Override
        public void run()
        {
            SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            assertThat(monitor, notNullValue());
            boolean rc = monitor.connect(monitorAddr);
            assertThat(rc, is(true));

            ZMQ.Event event = ZMQ.Event.read(monitor);
            if (event == null && monitor.errno() == ZError.ETERM) {
                monitor.close();
                return;
            }
            assertThat(event, notNullValue());

            this.event.set(event);
            monitor.close();
        }
    }

    private byte[] incompleteHandshake()
    {
        byte[] raw = new byte[10];

        raw[0] = (byte) 0xff;
        raw[9] = (byte) 0x1;

        return raw;
    }

    @Test
    public void testHandshakeTimeout() throws IOException, InterruptedException
    {
        int handshakeInterval = 10;

        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase socket = ctx.createSocket(ZMQ.ZMQ_REP);
        assertThat(socket, notNullValue());

        boolean rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_HANDSHAKE_IVL, handshakeInterval);
        assertThat(rc, is(true));

        rc = ZMQ.monitorSocket(socket, "inproc://monitor", ZMQ.ZMQ_EVENT_DISCONNECTED);
        assertThat(rc, is(true));

        SocketMonitor monitor = new SocketMonitor(ctx, "inproc://monitor");
        monitor.start();

        rc = ZMQ.bind(socket, "tcp://127.0.0.1:*");
        assertThat(rc, is(true));

        String endpoint = (String) ZMQ.getSocketOptionExt(socket, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        Socket sender = new Socket("127.0.0.1", TestUtils.port(endpoint));
        OutputStream out = sender.getOutputStream();
        out.write(incompleteHandshake());
        out.flush();

        monitor.join();

        // there shall be a disconnected event because of the handshake timeout
        final ZMQ.Event event = monitor.event.get();
        assertThat(event, notNullValue());
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_DISCONNECTED));

        out.close();
        sender.close();

        ZMQ.close(socket);
        ZMQ.term(ctx);
    }
}
