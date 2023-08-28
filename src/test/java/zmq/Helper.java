package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.NetProtocol;
import zmq.pipe.Pipe;
import zmq.util.Errno;

public class Helper
{
    public static final AtomicInteger counter = new AtomicInteger(2);

    private Helper()
    {
    }

    public static class DummyCtx extends Ctx
    {
    }

    public static class DummySocketChannel implements WritableByteChannel
    {
        private final int    bufsize;
        private final byte[] buf;

        public DummySocketChannel()
        {
            this(64);
        }

        public DummySocketChannel(int bufsize)
        {
            this.bufsize = bufsize;
            buf = new byte[bufsize];
        }

        public byte[] data()
        {
            return buf;
        }

        @Override
        public void close()
        {
        }

        @Override
        public boolean isOpen()
        {
            return true;
        }

        @Override
        public int write(ByteBuffer src)
        {
            int remaining = src.remaining();
            if (remaining > bufsize) {
                src.get(buf);
                return bufsize;
            }
            src.get(buf, 0, remaining);
            return remaining;
        }

    }

    public static final DummyCtx ctx = new DummyCtx();

    public static class DummyIOThread extends IOThread
    {
        public DummyIOThread()
        {
            super(ctx, 2);
        }
    }

    public static class DummySocket extends SocketBase
    {
        public DummySocket()
        {
            super(ctx, counter.get(), counter.get());
            counter.incrementAndGet();
        }

        @Override
        protected void xattachPipe(Pipe pipe, boolean icanhasall, boolean isLocallyInitiated)
        {
        }

        @Override
        protected void xpipeTerminated(Pipe pipe)
        {
        }

    }

    public static class DummySession extends SessionBase
    {
        public final List<Msg> out = new ArrayList<>();

        public DummySession()
        {
            this(new DummyIOThread(), false, new DummySocket(), new Options(), new Address(NetProtocol.tcp, "localhost:9090"));
        }

        public DummySession(IOThread ioThread, boolean connect, SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }

        @Override
        public boolean pushMsg(Msg msg)
        {
            System.out.println("session.write " + msg);
            out.add(msg);
            return true;
        }

        @Override
        public Msg pullMsg()
        {
            System.out.println("session.read " + out);
            if (out.isEmpty()) {
                return null;
            }

            return out.remove(0);
        }

    }

    public static void bounce(SocketBase sb, SocketBase sc)
    {
        byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET);

        //  Send the message.
        int rc = ZMQ.send(sc, content, 32, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.send(sc, content, 32, 0);
        assertThat(rc, is(32));

        //  Bounce the message back.
        Msg msg;
        msg = ZMQ.recv(sb, 0);
        assert (msg.size() == 32);
        long rcvmore = ZMQ.getSocketOption(sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 1);
        msg = ZMQ.recv(sb, 0);
        assert (rc == 32);
        rcvmore = ZMQ.getSocketOption(sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 0);
        rc = ZMQ.send(sb, new Msg(msg), ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.send(sb, new Msg(msg), 0);
        assert (rc == 32);

        //  Receive the bounced message.
        msg = ZMQ.recv(sc, 0);
        assert (rc == 32);
        rcvmore = ZMQ.getSocketOption(sc, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(1L));
        msg = ZMQ.recv(sc, 0);
        assert (rc == 32);
        rcvmore = ZMQ.getSocketOption(sc, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(0L));
        //  Check whether the message is still the same.
        //assert (memcmp (buf2, content, 32) == 0);
    }

    public static void expectBounceFail(SocketBase server, SocketBase client)
    {
        final byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET);
        final int timeout = 250;
        final Errno errno = new Errno();

        //  Send message from client to server
        ZMQ.setSocketOption(client, ZMQ.ZMQ_SNDTIMEO, timeout);

        int rc = ZMQ.send(client, content, 32, ZMQ.ZMQ_SNDMORE);
        assert ((rc == 32) || ((rc == -1) && errno.is(ZError.EAGAIN)));
        rc = ZMQ.send(client, content, 32, 0);
        assert ((rc == 32) || ((rc == -1) && errno.is(ZError.EAGAIN)));

        //  Receive message at server side (should not succeed)
        ZMQ.setSocketOption(server, ZMQ.ZMQ_RCVTIMEO, timeout);

        Msg msg = ZMQ.recv(server, 0);
        assert (msg == null);
        assert errno.is(ZError.EAGAIN);

        //  Send message from server to client to test other direction
        //  If connection failed, send may block, without a timeout
        ZMQ.setSocketOption(server, ZMQ.ZMQ_SNDTIMEO, timeout);

        rc = ZMQ.send(server, content, 32, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32 || ((rc == -1) && errno.is(ZError.EAGAIN)));
        rc = ZMQ.send(server, content, 32, 0);
        assert (rc == 32 || ((rc == -1) && errno.is(ZError.EAGAIN)));

        //  Receive message at client side (should not succeed)
        ZMQ.setSocketOption(client, ZMQ.ZMQ_RCVTIMEO, timeout);
        msg = ZMQ.recv(client, 0);
        assert (msg == null);
        assert errno.is(ZError.EAGAIN);
    }

    public static int send(SocketBase socket, String data)
    {
        return ZMQ.send(socket, data, 0);
    }

    public static int sendMore(SocketBase socket, String data)
    {
        return ZMQ.send(socket, data, ZMQ.ZMQ_SNDMORE);
    }

    public static String recv(SocketBase socket)
    {
        Msg msg = ZMQ.recv(socket, 0);
        assert (msg != null);
        return new String(msg.data(), ZMQ.CHARSET);
    }

    //  Sends a message composed of frames that are C strings or null frames.
    //  The list must be terminated by SEQ_END.
    //  Example: s_send_seq (req, "ABC", 0, "DEF", SEQ_END);
    public static void sendSeq(SocketBase socket, String... data)
    {
        int rc;
        for (int idx = 0; idx < data.length - 1; ++idx) {
            rc = sendMore(socket, data[idx]);
            assert (rc == data[idx].length());
        }
        rc = send(socket, data[data.length - 1]);
        assert (rc == data[data.length - 1].length());
    }

    //  Receives message a number of frames long and checks that the frames have
    //  the given data which can be either C strings or 0 for a null frame.
    //  The list must be terminated by SEQ_END.
    //  Example: s_recv_seq (rep, "ABC", 0, "DEF", SEQ_END);
    public static void recvSeq(SocketBase socket, String... data)
    {
        String rc;
        for (String datum : data) {
            rc = recv(socket);
            assert (datum.equals(rc));
        }
    }

    public static void send(Socket sa, String data) throws IOException
    {
        byte[] content = data.getBytes(ZMQ.CHARSET);

        byte[] length = String.format("%04d", content.length).getBytes(ZMQ.CHARSET);

        byte[] buf = new byte[1024];
        int reslen;
        int rc;
        //  Bounce the message back.
        InputStream in = sa.getInputStream();
        OutputStream out = sa.getOutputStream();

        out.write(length);
        out.write(content);

        System.out.println("sent " + data.length() + " " + data);
        int toRead = 4; // 4 + greeting_size
        int read = 0;
        while (toRead > 0) {
            rc = in.read(buf, read, toRead);
            read += rc;
            toRead -= rc;
            System.out.println("read " + rc + " total_read " + read + " toRead " + toRead);
        }
        System.out.printf("%02x %02x %02x %02x%n", buf[0], buf[1], buf[2], buf[3]);
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        reslen = Integer.parseInt(new String(buf, 0, 4, ZMQ.CHARSET));

        in.read(buf, 0, reslen);
        System.out.println("recv " + reslen + " " + new String(buf, 0, reslen, ZMQ.CHARSET));
    }
}
