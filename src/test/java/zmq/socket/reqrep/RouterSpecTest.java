package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.socket.AbstractSpecTest;

public class RouterSpecTest extends AbstractSpecTest
{
    @Test
    public void testFairQueueIn()
    {
        Ctx ctx = ZMQ.createContext();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:*");

        for (String bindAddress : binds) {
            // SHALL receive incoming messages from its peers using a fair-queuing
            // strategy.
            fairQueueIn(ctx, bindAddress, ZMQ.ZMQ_ROUTER, ZMQ.ZMQ_DEALER);
        }

        ZMQ.term(ctx);
    }

    @Test
    @Ignore
    public void testDestroyQueueOnDisconnect()
    {
        Ctx ctx = ZMQ.createContext();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:*");

        for (String bindAddress : binds) {
            // SHALL create a double queue when a peer connects to it. If this peer
            // disconnects, the ROUTER socket SHALL destroy its double queue and SHALL
            // discard any messages it contains.
            // *** Test disabled until libzmq does this properly ***
            // test_destroy_queue_on_disconnect (ctx);
        }

        ZMQ.term(ctx);
    }

    private void fairQueueIn(Ctx ctx, String address, int bindType, int connectType)
    {
        //  Server socket will accept connections
        SocketBase receiver = ZMQ.socket(ctx, bindType);
        assertThat(receiver, notNullValue());

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(receiver, ZMQ.ZMQ_RCVTIMEO, timeout);
        assertThat(rc, is(true));

        rc = ZMQ.bind(receiver, address);
        assertThat(rc, is(true));

        address = (String) ZMQ.getSocketOptionExt(receiver, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(address, notNullValue());

        int services = 5;
        List<SocketBase> senders = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase sender = ZMQ.socket(ctx, connectType);
            assertThat(sender, notNullValue());

            senders.add(sender);

            rc = ZMQ.setSocketOption(sender, ZMQ.ZMQ_RCVTIMEO, timeout);
            assertThat(rc, is(true));

            rc = ZMQ.setSocketOption(sender, ZMQ.ZMQ_IDENTITY, "A" + peer);
            assertThat(rc, is(true));

            rc = ZMQ.connect(sender, address);
            assertThat(rc, is(true));
        }

        rc = sendSeq(senders.get(0), "M");
        assertThat(rc, is(true));
        recvSeq(receiver, "A0", "M");

        rc = sendSeq(senders.get(0), "M");
        assertThat(rc, is(true));
        recvSeq(receiver, "A0", "M");

        Set<String> sum = new HashSet<>();

        // send N requests
        for (int peer = 0; peer < services; ++peer) {
            sendSeq(senders.get(peer), "M");
            sum.add("A" + peer);
        }

        // handle N requests
        for (int peer = 0; peer < services; ++peer) {
            Msg msg = ZMQ.recv(receiver, 0);
            assertThat(msg, notNullValue());
            assertThat(msg.size(), is(2));
            sum.remove(new String(msg.data(), ZMQ.CHARSET));
            recvSeq(receiver, "M");
        }
        assertThat(sum.size(), is(0));

        ZMQ.closeZeroLinger(receiver);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);

        }
        // Wait for disconnects.
        ZMQ.msleep(100);
    }
}
