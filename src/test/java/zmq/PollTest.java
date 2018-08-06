package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.Test;

import zmq.poll.PollItem;

public class PollTest
{
    @Test
    public void testPollTcp() throws Exception
    {
        Ctx context = ZMQ.init(1);
        assertThat(context, notNullValue());

        Selector selector = context.createSelector();
        assertThat(selector, notNullValue());

        PollItem[] items = new PollItem[2];

        ServerSocketChannel server = ServerSocketChannel.open();
        assertThat(server, notNullValue());
        server.configureBlocking(false);
        server.bind(null);
        InetSocketAddress addr = (InetSocketAddress) server.socket().getLocalSocketAddress();

        SocketChannel channelIn = SocketChannel.open();
        assertThat(channelIn, notNullValue());
        channelIn.configureBlocking(false);
        boolean rc = channelIn.connect(addr);
        assertThat(rc, is(false));

        SocketChannel channelOut = server.accept();
        channelOut.configureBlocking(false);

        rc = channelIn.finishConnect();
        assertThat(rc, is(true));

        items[0] = new PollItem(channelOut, ZMQ.ZMQ_POLLOUT);
        items[1] = new PollItem(channelIn, ZMQ.ZMQ_POLLIN);

        int counter = 0;
        boolean done = false;
        boolean sent = false;
        while (true) {
            int events = ZMQ.poll(selector, items, 1000);
            if (events < 0) {
                break;
            }
            counter++;

            if (items[0].isWritable() && !sent) {
                ByteBuffer bb = ByteBuffer.allocate(3);
                bb.put("tcp".getBytes());
                bb.flip();
                int written = channelOut.write(bb);
                assertThat(written, is(3));
                sent = true;
            }
            if (items[1].isReadable()) {
                ByteBuffer bb = ByteBuffer.allocate(3);
                int read = channelIn.read(bb);
                String r = new String(bb.array(), 0, read);
                assertThat(r, is("tcp"));
                done = true;
                break;
            }
        }
        assertThat(done, is(true));
        assertThat(counter, is(2));

        context.closeSelector(selector);
        ZMQ.term(context);

        channelIn.close();
        channelOut.close();
        server.close();
    }

    @Test
    public void testPollPipe() throws Exception
    {
        Ctx context = ZMQ.init(1);
        assertThat(context, notNullValue());

        Selector selector = context.createSelector();
        assertThat(selector, notNullValue());

        PollItem[] items = new PollItem[2];

        Pipe pipe = Pipe.open();
        assertThat(pipe, notNullValue());

        SinkChannel sink = pipe.sink();
        assertThat(sink, notNullValue());
        sink.configureBlocking(false);

        SourceChannel source = pipe.source();
        assertThat(source, notNullValue());
        source.configureBlocking(false);

        items[0] = new PollItem(sink, ZMQ.ZMQ_POLLOUT);
        items[1] = new PollItem(source, ZMQ.ZMQ_POLLIN);

        int counter = 0;
        boolean done = false;
        boolean sent = false;
        while (true) {
            int events = ZMQ.poll(selector, items, 1000);
            if (events < 0) {
                break;
            }
            counter++;

            if (items[0].isWritable() && !sent) {
                ByteBuffer bb = ByteBuffer.allocate(4);
                bb.put("pipe".getBytes());
                bb.flip();
                int written = sink.write(bb);
                assertThat(written, is(4));
                sent = true;
            }
            if (items[1].isReadable()) {
                ByteBuffer bb = ByteBuffer.allocate(5);
                int read = source.read(bb);
                String r = new String(bb.array(), 0, read);
                assertThat(r, is("pipe"));
                done = true;
                break;
            }
        }
        assertThat(done, is(true));
        assertThat(counter, is(2));

        context.closeSelector(selector);
        ZMQ.term(context);

        sink.close();
        source.close();
    }

    @Test
    public void testPollUdp() throws Exception
    {
        Ctx context = ZMQ.init(1);
        assertThat(context, notNullValue());

        Selector selector = context.createSelector();
        assertThat(selector, notNullValue());

        PollItem[] items = new PollItem[2];

        DatagramChannel udpIn = DatagramChannel.open();
        assertThat(udpIn, notNullValue());
        udpIn.configureBlocking(false);
        udpIn.socket().bind(null);
        InetSocketAddress addr = (InetSocketAddress) udpIn.socket().getLocalSocketAddress();

        DatagramChannel udpOut = DatagramChannel.open();
        assertThat(udpOut, notNullValue());
        udpOut.configureBlocking(false);
        udpOut.connect(addr);

        items[0] = new PollItem(udpOut, ZMQ.ZMQ_POLLOUT);
        items[1] = new PollItem(udpIn, ZMQ.ZMQ_POLLIN);

        int counter = 0;
        boolean done = false;
        boolean sent = false;
        while (true) {
            int events = ZMQ.poll(selector, items, 1000);
            if (events < 0) {
                break;
            }
            counter++;

            if (items[0].isWritable() && !sent) {
                ByteBuffer bb = ByteBuffer.allocate(3);
                bb.put("udp".getBytes());
                bb.flip();
                int written = udpOut.send(bb, addr);
                assertThat(written, is(3));
                sent = true;
            }
            if (items[1].isReadable()) {
                ByteBuffer bb = ByteBuffer.allocate(3);
                udpIn.receive(bb);
                String read = new String(bb.array(), 0, bb.limit());
                assertThat(read, is("udp"));
                done = true;
                break;
            }
        }
        assertThat(done, is(true));
        assertThat(counter, is(2));

        context.closeSelector(selector);
        ZMQ.term(context);

        udpIn.close();
        udpOut.close();
    }
}
