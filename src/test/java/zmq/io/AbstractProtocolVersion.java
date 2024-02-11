package zmq.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.ZMQ.Event;
import zmq.util.TestUtils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractProtocolVersion
{
    protected static final int REPETITIONS = 1000;
    private static final AtomicReference<Throwable> monitorFailure = new AtomicReference<>();

    static class SocketMonitor extends Thread
    {
        private final Ctx         ctx;
        private final String      monitorAddr;
        private final ZMQ.Event[] events = new ZMQ.Event[1];

        public SocketMonitor(Ctx ctx, String monitorAddr)
        {
            this.ctx = ctx;
            this.monitorAddr = monitorAddr;
            monitorFailure.set(null);
            this.setUncaughtExceptionHandler((t, ex) -> {
                ex.printStackTrace();
                monitorFailure.set(ex);
            });
        }

        @Override
        public void run()
        {
            SocketBase s = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            boolean rc = s.connect(monitorAddr);
            assertThat(rc, is(true));
            // Only some of the exceptional events could fire

            ZMQ.Event event = ZMQ.Event.read(s);
            if (event == null && s.errno() == ZError.ETERM) {
                s.close();
                return;
            }
            assertThat(event, notNullValue());

            events[0] = event;
            s.close();
        }
    }

    protected byte[] assertProtocolVersion(int version, List<ByteBuffer> raws, String payload)
            throws IOException, InterruptedException
    {
        String host = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase receiver = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(receiver, notNullValue());

        boolean rc = ZMQ.setSocketOption(receiver, ZMQ.ZMQ_LINGER, 0);
        assertThat(rc, is(true));

        rc = ZMQ.monitorSocket(receiver, "inproc://monitor", ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL);
        assertThat(rc, is(true));

        SocketMonitor monitor = new SocketMonitor(ctx, "inproc://monitor");
        monitor.start();

        rc = ZMQ.bind(receiver, host);
        assertThat(rc, is(true));

        String ep = (String) ZMQ.getSocketOptionExt(receiver, ZMQ.ZMQ_LAST_ENDPOINT);
        int port = TestUtils.port(ep);
        Socket sender = new Socket("127.0.0.1", port);
        OutputStream out = sender.getOutputStream();
        for (ByteBuffer raw : raws) {
            out.write(raw.array());
        }
        assertThat(monitorFailure.get(), nullValue());

        Msg msg = ZMQ.recv(receiver, 0);
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data(), ZMQ.CHARSET), is(payload));

        monitor.join();

        final Event event = monitor.events[0];
        assertThat(event, notNullValue());
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL));
        assertThat((Integer) event.arg, is(version));

        InputStream in = sender.getInputStream();
        byte[] data = new byte[255];
        int read = in.read(data);

        sender.close();

        ZMQ.close(receiver);
        ZMQ.term(ctx);

        return Arrays.copyOf(data, read);
    }

    protected List<ByteBuffer> raws(int revision)
    {
        List<ByteBuffer> raws = new ArrayList<>();
        ByteBuffer raw = ByteBuffer.allocate(12);
        // send V1 header
        raw.put((byte) 0xff).put(new byte[8]).put((byte) 0x1);
        // protocol revision
        raw.put((byte) revision);
        // socket type
        raw.put((byte) ZMQ.ZMQ_PUSH);

        raws.add(raw);
        return raws;
    }

    protected ByteBuffer identity()
    {
        return ByteBuffer.allocate(2)
                // size
                .put((byte) 1)
                // flags
                .put((byte) 0);
    }
}
