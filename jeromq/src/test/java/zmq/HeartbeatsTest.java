package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import zmq.util.AndroidProblematic;
import zmq.util.TestUtils;
import zmq.util.function.BiFunction;

public class HeartbeatsTest
{
    private static final long MAX = 100_000;

    // Tests sequentiality of received messages while heartbeating
    @Test
    @AndroidProblematic
    public void testSequentialityReceivedMessagesMultiThreadedPushBindPullConnect()
            throws IOException, InterruptedException
    {
        testSequentialityReceivedMessagesMultiThreaded(SocketBase::bind, SocketBase::connect);
    }

    // Tests sequentiality of received messages while heartbeating
    @Test
    @AndroidProblematic
    public void testSequentialityReceivedMessagesMultiThreadedPushConnectPullBind()
            throws IOException, InterruptedException
    {
        testSequentialityReceivedMessagesMultiThreaded(SocketBase::connect, SocketBase::bind);
    }

    private void testSequentialityReceivedMessagesMultiThreaded(BiFunction<SocketBase, String, Boolean> endpointPush, BiFunction<SocketBase, String, Boolean> endpointPull)
            throws IOException, InterruptedException
    {
        final int port = Utils.findOpenPort();
        final String host = "localhost";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        final SocketBase push = ctx.createSocket(ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());

        boolean rc = ZMQ.setSocketOption(push, ZMQ.ZMQ_HEARTBEAT_IVL, 200);
        assertThat(rc, is(true));

        rc = endpointPush.apply(push, String.format("tcp://%s:%s", host, port));
        assertThat(rc, is(true));

        final SocketBase pull = ctx.createSocket(ZMQ.ZMQ_PULL);
        rc = endpointPull.apply(pull, String.format("tcp://%s:%s", host, port));
        assertThat(rc, is(true));

        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(() -> {
            Thread.currentThread().setName("Push");
            long counter = 0;
            while (++counter < MAX) {
                String data = Long.toString(counter);
                int sent = ZMQ.send(push, Long.toString(counter), 0);
                assertThat(sent, is(data.length()));
            }
            System.out.println("Push finished");
            push.close();
        });
        service.submit(() -> {
            Thread.currentThread().setName("Pull");
            long counter = 0;
            while (++counter < MAX) {
                Msg msg = ZMQ.recv(pull, 0);
                assertThat(msg, notNullValue());

                long received = Long.parseLong(new String(msg.data(), ZMQ.CHARSET));
                assertThat(received, is(counter));

                if (counter % (MAX / 10) == 0) {
                    System.out.print(counter + " ");
                }
            }
            System.out.println("Pull finished");
            pull.close();
        });

        service.shutdown();
        service.awaitTermination(100, TimeUnit.SECONDS);
        ctx.terminate();
    }

    // Tests sequentiality of received messages while heartbeating
    @Test
    public void testSequentialityReceivedMessagesSingleThread() throws IOException
    {
        final int port = Utils.findOpenPort();
        final String host = "localhost";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ctx.createSocket(ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.setSocketOption(push, ZMQ.ZMQ_HEARTBEAT_IVL, 200);
        assertThat(rc, is(true));
        rc = push.connect(String.format("tcp://%s:%s", host, port));
        assertThat(rc, is(true));

        SocketBase pull = ctx.createSocket(ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = pull.bind(String.format("tcp://%s:%s", host, port));
        assertThat(rc, is(true));

        long counter = 0;
        while (++counter < MAX / 10) {
            String data = Long.toString(counter);
            int sent = ZMQ.send(push, Long.toString(counter), 0);
            assertThat(sent, is(data.length()));

            Msg msg = ZMQ.recv(pull, 0);
            assertThat(msg, notNullValue());
            long received = Long.parseLong(new String(msg.data(), ZMQ.CHARSET));
            assertThat(received, is(counter));

            if (counter % (MAX / 100) == 0) {
                System.out.print(counter + " ");
            }
        }
        System.out.println();

        push.close();
        pull.close();
        ctx.terminate();
    }

    // this checks for heartbeat in REQ socket.
    @Test
    public void testHeartbeatReq()
    {
        final int heartbeatInterval = 100;

        String addr = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase req = ctx.createSocket(ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());
        boolean rc = req.setSocketOpt(ZMQ.ZMQ_HEARTBEAT_IVL, heartbeatInterval);
        assertThat(rc, is(true));
        SocketBase rep = ctx.createSocket(ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        rc = rep.bind(addr);
        assertThat(rc, is(true));

        addr = (String) ZMQ.getSocketOptionExt(rep, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(addr, notNullValue());

        rc = req.connect(addr);
        assertThat(rc, is(true));

        final long start = System.currentTimeMillis();
        do {
            // request
            int sent = ZMQ.send(req, "hello", 0);
            assertThat(sent, is("hello".length()));

            Msg msg = ZMQ.recv(rep, 0);
            assertThat(msg, notNullValue());
            // reply
            sent = ZMQ.send(rep, "world", 0);
            assertThat(sent, is("world".length()));
            msg = ZMQ.recv(req, 0);
            assertThat(msg, notNullValue());
            // let some time pass so several heartbeats are sent
        } while (System.currentTimeMillis() - start < 5 * heartbeatInterval);

        rep.close();
        req.close();
        ctx.terminate();
    }

    // This checks for a broken TCP connection (or, in this case a stuck one
    // where the peer never responds to PINGS). There should be an accepted event
    // then a disconnect event.
    @Test
    public void testHeartbeatTimeout() throws IOException
    {
        testHeartbeatTimeout(false);
    }

    @Test
    public void testHeartbeatTimeoutWithContext() throws IOException
    {
        testHeartbeatTimeout(true);
    }

    private void testHeartbeatTimeout(boolean mockPing) throws IOException
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase server = prepServerSocket(ctx, !mockPing, false);
        assertThat(server, notNullValue());

        SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        boolean rc = monitor.connect("inproc://monitor");
        assertThat(rc, is(true));

        String endpoint = (String) ZMQ.getSocketOptionExt(server, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        Socket socket = new Socket("127.0.0.1", TestUtils.port(endpoint));

        // Mock a ZMTP 3 client so we can forcibly time out a connection
        mockHandshake(socket);
        if (mockPing) {
            mockPing(socket);
        }

        // By now everything should report as connected
        ZMQ.Event event = ZMQ.Event.read(monitor);
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_ACCEPTED));

        if (!mockPing) {
            // We should have been disconnected
            event = ZMQ.Event.read(monitor);
            assertThat(event.event, is(ZMQ.ZMQ_EVENT_DISCONNECTED));
        }

        socket.close();

        ZMQ.close(monitor);
        ZMQ.close(server);
        ZMQ.term(ctx);
    }

    // This checks that peers respect the TTL value in ping messages
    // We set up a mock ZMTP 3 client and send a ping message with a TTL
    // to a server that is not doing any heartbeating. Then we sleep,
    // if the server disconnects the client, then we know the TTL did
    // its thing correctly.
    @Test
    public void testHeartbeatTtl()
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase server = prepServerSocket(ctx, false, false);
        assertThat(server, notNullValue());

        SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        boolean rc = monitor.connect("inproc://monitor");
        assertThat(rc, is(true));

        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        // Set the heartbeat TTL to 0.1 seconds
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HEARTBEAT_TTL, 100);
        assertThat(rc, is(true));

        // Set the heartbeat interval to much longer than the TTL so that
        // the socket times out on the remote side.
        rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_HEARTBEAT_IVL, 250);
        assertThat(rc, is(true));

        String endpoint = (String) ZMQ.getSocketOptionExt(server, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        rc = ZMQ.connect(client, endpoint);
        assertThat(rc, is(true));

        // By now everything should report as connected
        ZMQ.Event event = ZMQ.Event.read(monitor);
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_ACCEPTED));

        ZMQ.msleep(100);

        // We should have been disconnected
        event = ZMQ.Event.read(monitor);
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_DISCONNECTED));

        ZMQ.close(monitor);
        ZMQ.close(server);
        ZMQ.close(client);
        ZMQ.term(ctx);
    }

    // This checks for normal operation - that is pings and pongs being
    // exchanged normally. There should be an accepted event on the server,
    // and then no event afterwards.
    @Test
    public void testHeartbeatNoTimeoutWithCurve()
    {
        testHeartbeatNoTimeout(true, new byte[0]);
    }

    @Test
    public void testHeartbeatNoTimeoutWithoutCurve()
    {
        testHeartbeatNoTimeout(false, new byte[0]);
    }

    @Test
    public void testHeartbeatNoTimeoutWithoutCurveWithPingContext()
    {
        testHeartbeatNoTimeout(false, "context".getBytes(ZMQ.CHARSET));
    }

    private void testHeartbeatNoTimeout(boolean curve, byte[] context)
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase server = prepServerSocket(ctx, true, context, curve);
        assertThat(server, notNullValue());

        SocketBase monitor = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
        boolean rc = monitor.connect("inproc://monitor");
        assertThat(rc, is(true));

        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(client, notNullValue());

        if (curve) {
            setupCurve(client, false);
        }

        String endpoint = (String) ZMQ.getSocketOptionExt(server, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(endpoint, notNullValue());

        rc = ZMQ.connect(client, endpoint);
        assertThat(rc, is(true));

        ZMQ.msleep(400);

        // By now everything should report as connected
        ZMQ.Event event = ZMQ.Event.read(monitor);
        assertThat(event.event, is(ZMQ.ZMQ_EVENT_ACCEPTED));

        // We should still be connected because pings and pongs are happenin'
        event = ZMQ.Event.read(monitor, ZMQ.ZMQ_DONTWAIT);
        assertThat(event, nullValue());

        ZMQ.close(monitor);
        ZMQ.close(server);
        ZMQ.close(client);
        ZMQ.term(ctx);
    }

    private SocketBase prepServerSocket(Ctx ctx, boolean heartBeats, boolean curve)
    {
        return prepServerSocket(ctx, heartBeats, new byte[0], curve);
    }

    private SocketBase prepServerSocket(Ctx ctx, boolean heartBeats, byte[] pingContext, boolean curve)
    {
        SocketBase server = ctx.createSocket(ZMQ.ZMQ_ROUTER);
        assertThat(server, notNullValue());

        boolean rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_LINGER, 0);
        assertThat(rc, is(true));

        if (heartBeats) {
            rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_HEARTBEAT_IVL, 200);
            assertThat(rc, is(true));

            rc = ZMQ.setSocketOption(server, ZMQ.ZMQ_HEARTBEAT_CONTEXT, pingContext);
            assertThat(rc, is(true));
        }

        if (curve) {
            setupCurve(server, true);
        }

        rc = ZMQ.bind(server, "tcp://127.0.0.1:*");
        assertThat(rc, is(true));

        rc = ZMQ.monitorSocket(
                server,
                "inproc://monitor",
                ZMQ.ZMQ_EVENT_CONNECTED | ZMQ.ZMQ_EVENT_DISCONNECTED | ZMQ.ZMQ_EVENT_ACCEPTED);
        assertThat(rc, is(true));

        return server;
    }

    private void setupCurve(SocketBase socket, boolean server)
    {
        String secretKey;
        String publicKey = "Yne@$w-vo<fVvi]a<NY6T1ed:M$fCG*[IaLV{hID";
        String serverKey;
        if (server) {
            boolean rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_IDENTITY, "IDENT");
            assertThat(rc, is(true));

            secretKey = "JTKVSB%%)wK0E.X)V>+}o?pNmC{O&4W4b!Ni{Lh6";
            publicKey = "rq:rM>}U?@Lns47E1%kR.o@n%FcmmsL/@{H8]yf7";
            serverKey = null;
        }
        else {
            secretKey = "D:)Q[IlAW!ahhC2ac:9*A}h:p?([4%wOTJ%JR%cs";
            serverKey = "rq:rM>}U?@Lns47E1%kR.o@n%FcmmsL/@{H8]yf7";
        }
        boolean rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_CURVE_SECRETKEY, secretKey);
        assertThat(rc, is(true));
        rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_CURVE_PUBLICKEY, publicKey);
        assertThat(rc, is(true));
        if (server) {
            rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_CURVE_SERVER, true);
            assertThat(rc, is(true));
        }
        else {
            rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_CURVE_SERVERKEY, serverKey);
            assertThat(rc, is(true));
        }
    }

    private void mockHandshake(Socket socket) throws IOException
    {
        byte[] greetings = new byte[64];
        greetings[0] = (byte) 0xff;
        greetings[9] = (byte) 0x7f;
        greetings[10] = (byte) 3;
        greetings[12] = (byte) 'N';
        greetings[13] = (byte) 'U';
        greetings[14] = (byte) 'L';
        greetings[15] = (byte) 'L';

        OutputStream out = socket.getOutputStream();
        out.write(greetings);
        out.flush();

        recvWithRetry(socket, 64);

        byte[] ready = new byte[43];
        int idx = 0;
        ready[idx++] = (byte) 4;
        ready[idx++] = (byte) 41;
        ready[idx++] = (byte) 5;
        ready[idx++] = (byte) 'R';
        ready[idx++] = (byte) 'E';
        ready[idx++] = (byte) 'A';
        ready[idx++] = (byte) 'D';
        ready[idx++] = (byte) 'Y';
        ready[idx++] = (byte) 11;
        ready[idx++] = (byte) 'S';
        ready[idx++] = (byte) 'o';
        ready[idx++] = (byte) 'c';
        ready[idx++] = (byte) 'k';
        ready[idx++] = (byte) 'e';
        ready[idx++] = (byte) 't';
        ready[idx++] = (byte) '-';
        ready[idx++] = (byte) 'T';
        ready[idx++] = (byte) 'y';
        ready[idx++] = (byte) 'p';
        ready[idx++] = (byte) 'e';
        ready[idx++] = (byte) 0;
        ready[idx++] = (byte) 0;
        ready[idx++] = (byte) 0;
        ready[idx++] = (byte) 6;
        ready[idx++] = (byte) 'D';
        ready[idx++] = (byte) 'E';
        ready[idx++] = (byte) 'A';
        ready[idx++] = (byte) 'L';
        ready[idx++] = (byte) 'E';
        ready[idx++] = (byte) 'R';
        ready[idx++] = (byte) 8;
        ready[idx++] = (byte) 'I';
        ready[idx++] = (byte) 'd';
        ready[idx++] = (byte) 'e';
        ready[idx++] = (byte) 'n';
        ready[idx++] = (byte) 't';
        ready[idx++] = (byte) 'i';
        ready[idx++] = (byte) 't';
        ready[idx++] = (byte) 'y';

        out.write(ready);
        out.flush();

        recvWithRetry(socket, 43);

    }

    private void mockPing(Socket socket) throws IOException
    {
        //  test PING context - should be replicated in the PONG
        //  to avoid timeouts, do a bulk send
        byte[] ping = new byte[12];
        int idx = 0;
        ping[idx++] = 4;
        ping[idx++] = 10;
        ping[idx++] = 4;
        ping[idx++] = 'P';
        ping[idx++] = 'I';
        ping[idx++] = 'N';
        ping[idx++] = 'G';
        ping[idx++] = 0;
        ping[idx++] = 0;
        ping[idx++] = 'L';
        ping[idx++] = 'O';
        ping[idx++] = 'L';

        OutputStream out = socket.getOutputStream();
        out.write(ping);
        out.flush();

        //  test a larger body that won't fit in a small message
        // and should get truncated
        ping = new byte[65];
        idx = 0;
        ping[idx++] = 4;
        ping[idx++] = 65;
        ping[idx++] = 4;
        ping[idx++] = 'P';
        ping[idx++] = 'I';
        ping[idx++] = 'N';
        ping[idx++] = 'G';
        ping[idx++] = 0;
        ping[idx++] = 0;
        ping[idx++] = 'L';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'L';

        out.write(ping);
        out.flush();

        //  small pong
        ping = new byte[10];
        idx = 0;
        ping[idx++] = 4;
        ping[idx++] = 8;
        ping[idx++] = 4;
        ping[idx++] = 'P';
        ping[idx++] = 'O';
        ping[idx++] = 'N';
        ping[idx++] = 'G';
        ping[idx++] = 'L';
        ping[idx++] = 'O';
        ping[idx++] = 'L';

        byte[] pong = recvWithRetry(socket, 10);
        assertThat(pong, is(ping));

        //  large pong
        ping = new byte[23];
        idx = 0;
        ping[idx++] = 4;
        ping[idx++] = 21;
        ping[idx++] = 4;
        ping[idx++] = 'P';
        ping[idx++] = 'O';
        ping[idx++] = 'N';
        ping[idx++] = 'G';
        ping[idx++] = 'L';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';
        ping[idx++] = 'O';

        out.write(ping);
        out.flush();

        pong = recvWithRetry(socket, 23);
        assertThat(pong, is(ping));
    }

    private byte[] recvWithRetry(Socket socket, int bytes) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int received = 0;
        byte[] data = new byte[bytes];
        while (true) {
            InputStream in = socket.getInputStream();
            int rc = in.read(data, received, bytes - received);
            if (rc < 0) {
                break;
            }
            out.write(data, received, rc);
            received += rc;
            assert (received <= bytes);
            if (received == bytes) {
                break;
            }
        }
        return out.toByteArray();
    }
}
