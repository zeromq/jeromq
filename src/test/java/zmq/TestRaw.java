package zmq;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestRaw
{
    @Test
    public void testRaw2dealer()
    {
        final byte[] standardGreeting = new byte[64];
        standardGreeting[0] = (byte) 0xff;
        standardGreeting[8] = 1;
        standardGreeting[9] = 0x7f;
        standardGreeting[10] = 3;
        standardGreeting[12] = 'N';
        standardGreeting[13] = 'U';
        standardGreeting[14] = 'L';
        standardGreeting[15] = 'L';

        String host = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  We'll be using this socket in raw mode
        SocketBase raw = ZMQ.socket(ctx, ZMQ.ZMQ_RAW);
        assertThat(raw, notNullValue());

        boolean rc = ZMQ.setSocketOption(raw, ZMQ.ZMQ_LINGER, 0);
        assertThat(rc, is(true));

        rc = ZMQ.bind(raw, host);
        assertThat(rc, is(true));

        host = (String) ZMQ.getSocketOptionExt(raw, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        //  We'll be using this socket as the other peer
        SocketBase dealer = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());
        rc = ZMQ.setSocketOption(dealer, ZMQ.ZMQ_LINGER, 0);
        assertThat(rc, is(true));

        rc = ZMQ.connect(dealer, host);
        assertThat(rc, is(true));

        //  Send a message on the dealer socket
        int ret = ZMQ.send(dealer, "Hello", 0);
        assertThat(ret, is(5));

        //  Connecting sends a zero message
        Msg zero = ZMQ.recv(raw, 0);
        assertThat(zero, notNullValue());
        assertThat(zero.size(), is(0));

        //  Real data follows
        Msg greeting = ZMQ.recv(raw, 0);
        assertThat(greeting, notNullValue());
        assertThat(greeting.size(), is(10));
        assertThat(greeting.data(), is(new byte[] { (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 1, 0x7f }));

        //  Send our own protocol greeting
        Msg ourGreeting = new Msg(standardGreeting);
        ourGreeting.setRoutingId(greeting.getRoutingId());
        ret = ZMQ.send(raw, ourGreeting, 0);
        assertThat(ret, is(standardGreeting.length));

        //  Now we expect the data from the DEALER socket
        //  We want the rest of greeting along with the Ready command
        int bytesRead = 0;
        ByteBuffer read = ByteBuffer.allocate(100);
        while (bytesRead < 97) {
            Msg msg = ZMQ.recv(raw, 0);
            assertThat(msg, notNullValue());
            bytesRead += msg.size();
            read.put(msg.buf());
        }
        assertThat(read.get(0), is((byte) 3));
        assertThat(read.get(1), is((byte) 0));
        assertThat(read.get(2), is((byte) 'N'));
        assertThat(read.get(3), is((byte) 'U'));
        assertThat(read.get(4), is((byte) 'L'));
        assertThat(read.get(5), is((byte) 'L'));
        for (int idx = 0; idx < 16; ++idx) {
            assertThat(read.get(2 + 4 + idx), is((byte) 0));
        }
        assertThat(read.get(54), is((byte) 4));
        assertThat(read.get(55), is((byte) 051)); // octal notation, yes
        assertThat(read.get(56), is((byte) 5));
        assertThat(read.get(57), is((byte) 'R'));
        assertThat(read.get(58), is((byte) 'E'));
        assertThat(read.get(59), is((byte) 'A'));
        assertThat(read.get(60), is((byte) 'D'));
        assertThat(read.get(61), is((byte) 'Y'));

        assertThat(read.get(62), is((byte) 013));
        assertThat(read.get(63), is((byte) 'S'));
        assertThat(read.get(64), is((byte) 'o'));
        assertThat(read.get(65), is((byte) 'c'));
        assertThat(read.get(66), is((byte) 'k'));
        assertThat(read.get(67), is((byte) 'e'));
        assertThat(read.get(68), is((byte) 't'));
        assertThat(read.get(69), is((byte) '-'));
        assertThat(read.get(70), is((byte) 'T'));
        assertThat(read.get(71), is((byte) 'y'));
        assertThat(read.get(72), is((byte) 'p'));
        assertThat(read.get(73), is((byte) 'e'));

        assertThat(read.get(74), is((byte) 0));
        assertThat(read.get(75), is((byte) 0));
        assertThat(read.get(76), is((byte) 0));
        assertThat(read.get(77), is((byte) 6));
        assertThat(read.get(78), is((byte) 'D'));
        assertThat(read.get(79), is((byte) 'E'));
        assertThat(read.get(80), is((byte) 'A'));
        assertThat(read.get(81), is((byte) 'L'));
        assertThat(read.get(82), is((byte) 'E'));
        assertThat(read.get(83), is((byte) 'R'));

        assertThat(read.get(84), is((byte) 010));
        assertThat(read.get(85), is((byte) 'I'));
        assertThat(read.get(86), is((byte) 'd'));
        assertThat(read.get(87), is((byte) 'e'));
        assertThat(read.get(88), is((byte) 'n'));
        assertThat(read.get(89), is((byte) 't'));
        assertThat(read.get(90), is((byte) 'i'));
        assertThat(read.get(91), is((byte) 't'));
        assertThat(read.get(92), is((byte) 'y'));
        assertThat(read.get(93), is((byte) 0));
        assertThat(read.get(94), is((byte) 0));
        assertThat(read.get(95), is((byte) 0));
        assertThat(read.get(96), is((byte) 0));

        //  Send Ready command
        Msg ourReady = new Msg(
                new byte[] { 4, 41, 5, 'R', 'E', 'A', 'D', 'Y', 11, 'S', 'o', 'c', 'k', 'e', 't', '-', 'T', 'y', 'p',
                        'e', 0, 0, 0, 6, 'R', 'O', 'U', 'T', 'E', 'R', 8, 'I', 'd', 'e', 'n', 't', 'i', 't', 'y', 0, 0,
                        0, 0 });
        ourReady.setRoutingId(greeting.getRoutingId());
        ZMQ.send(raw, ourReady, 0);

        //  Now we expect the data from the DEALER socket
        Msg un = ZMQ.recv(raw, 0);
        assertThat(un, notNullValue());
        assertThat(un.size(), is(7));

        //  Then we have a 5-byte message "Hello"
        assertThat(un.get(0), is((byte) 0));
        assertThat(un.get(1), is((byte) 5));
        assertThat(un.get(2), is((byte) 'H'));
        assertThat(un.get(3), is((byte) 'e'));
        assertThat(un.get(4), is((byte) 'l'));
        assertThat(un.get(5), is((byte) 'l'));
        assertThat(un.get(6), is((byte) 'o'));

        //  Send "World" back to DEALER
        Msg backWorld = new Msg(new byte[] { 0, 5, 'W', 'o', 'r', 'l', 'd' });
        backWorld.setRoutingId(greeting.getRoutingId());
        ret = ZMQ.send(raw, backWorld, 0);
        assertThat(ret, is(7));

        //  Expect response on DEALER socket
        Msg recv = ZMQ.recv(dealer, 0);
        assertThat(recv.size(), is(5));
        assertThat(recv.data(), is("World".getBytes(ZMQ.CHARSET)));

        ZMQ.close(raw);
        ZMQ.close(dealer);

        ZMQ.term(ctx);
    }

    @Test
    public void testRaw2Raw()
    {
        String host = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Set up listener STREAM.
        SocketBase bind = ZMQ.socket(ctx, ZMQ.ZMQ_RAW);
        assertThat(bind, notNullValue());

        boolean rc = ZMQ.bind(bind, host);
        assertThat(rc, is(true));

        host = (String) ZMQ.getSocketOptionExt(bind, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(host, notNullValue());

        //  Set up connection stream.
        SocketBase connect = ZMQ.socket(ctx, ZMQ.ZMQ_RAW);
        assertThat(connect, notNullValue());

        //  Do the connection.
        int peerRoutingId = ZMQ.connectPeer(connect, host);
        assertThat(peerRoutingId, not(0));

        ZMQ.sleep(1);

        //  Connecting sends a zero message
        Msg msg = ZMQ.recv(bind, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size(), is(0));

        ZMQ.close(bind);
        ZMQ.close(connect);

        ZMQ.term(ctx);
    }
}
