package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;

import zmq.poll.PollItem;

public class PollTest
{
    interface TxRx<R>
    {
        R apply(ByteBuffer bb) throws IOException;
    }

    @Test(timeout = 1000)
    public void testPollTcp() throws IOException
    {
        ServerSocketChannel server = ServerSocketChannel.open();
        assertThat(server, notNullValue());
        server.configureBlocking(true);
        server.socket().bind(null);
        InetSocketAddress addr = (InetSocketAddress) server.socket().getLocalSocketAddress();

        SocketChannel in = SocketChannel.open();
        assertThat(in, notNullValue());
        in.configureBlocking(false);
        boolean rc = in.connect(addr);
        assertThat(rc, is(false));

        SocketChannel out = server.accept();
        out.configureBlocking(false);

        rc = in.finishConnect();
        assertThat(rc, is(true));

        try {
            assertPoller(in, out, out::write, in::read);
        }
        finally {
            in.close();
            out.close();
            server.close();
        }
    }

    @Test(timeout = 1000)
    public void testPollPipe() throws IOException
    {
        Pipe pipe = Pipe.open();
        assertThat(pipe, notNullValue());

        SinkChannel sink = pipe.sink();
        assertThat(sink, notNullValue());
        sink.configureBlocking(false);

        SourceChannel source = pipe.source();
        assertThat(source, notNullValue());
        source.configureBlocking(false);

        try {
            assertPoller(source, sink, sink::write, source::read);
        }
        finally {
            sink.close();
            source.close();
        }

    }

    @Test(timeout = 1000)
    public void testPollUdp() throws IOException
    {
        DatagramChannel in = DatagramChannel.open();
        assertThat(in, notNullValue());
        in.configureBlocking(false);
        in.socket().bind(null);
        InetSocketAddress addr = (InetSocketAddress) in.socket().getLocalSocketAddress();

        DatagramChannel out = DatagramChannel.open();
        assertThat(out, notNullValue());
        out.configureBlocking(false);
        out.connect(addr);

        try {
            assertPoller(in, out, bb -> out.send(bb, addr), in::receive);
        }
        finally {
            in.close();
            out.close();
        }
    }

    private <T extends SelectableChannel> void assertPoller(T in, T out, TxRx<Integer> tx, TxRx<Object> rx)
            throws IOException
    {
        Ctx context = ZMQ.init(1);
        assertThat(context, notNullValue());

        Selector selector = context.createSelector();
        assertThat(selector, notNullValue());

        PollItem[] items = new PollItem[2];

        items[0] = new PollItem(out, ZMQ.ZMQ_POLLOUT);
        items[1] = new PollItem(in, ZMQ.ZMQ_POLLIN);

        String payload = UUID.randomUUID().toString();
        boolean sending = true;
        while (true) {
            int events = ZMQ.poll(selector, items, 1000);
            if (events < 0) {
                fail("unable to poll events");
            }

            if (sending && items[0].isWritable()) {
                sending = false;
                ByteBuffer bb = ByteBuffer.allocate(payload.length());
                bb.put(payload.getBytes());
                bb.flip();
                int written = tx.apply(bb);

                assertThat(written, is(payload.length()));
            }
            if (!sending && items[1].isReadable()) {
                ByteBuffer bb = ByteBuffer.allocate(payload.length());
                rx.apply(bb);
                String read = new String(bb.array(), 0, bb.limit());

                assertThat(read, is(payload));
                break;
            }
        }

        context.closeSelector(selector);
        ZMQ.term(context);
    }

    @Ignore
    @Test(timeout = 1000)
    public void testRepeated() throws IOException
    {
        for (int idx = 0; idx < 10_000_000; ++idx) {
            if (idx % 10000 == 0) {
                System.out.println(idx);
            }
            testPollTcp();
            testPollUdp();
        }
    }
}
