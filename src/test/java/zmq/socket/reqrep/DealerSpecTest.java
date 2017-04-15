package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.socket.AbstractSpecTest;
import zmq.util.Utils;

public class DealerSpecTest extends AbstractSpecTest
{
    @Test
    public void testSpecFairQueueIn() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // SHALL receive incoming messages from its peers using a fair-queuing
            // strategy.
            fairQueueIn(ctx, bindAddress, ZMQ.ZMQ_DEALER, ZMQ.ZMQ_DEALER);
        }

        ZMQ.term(ctx);
    }

    @Test
    public void testSpecRoundRobinOut() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // SHALL route outgoing messages to available peers using a round-robin
            // strategy.
            roundRobinOut(ctx, bindAddress, ZMQ.ZMQ_DEALER, ZMQ.ZMQ_REP);
        }

        ZMQ.term(ctx);
    }

    @Test
    public void testSpecBlockOnSendNoPeers() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // SHALL block on sending, or return a suitable error, when it has no connected peers.
            blockOnSendNoPeers(ctx, bindAddress, ZMQ.ZMQ_DEALER);
        }

        ZMQ.term(ctx);
    }

    @Test
    @Ignore
    public void testSpecDestroyQueueOnDisconnect() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // SHALL create a double queue when a peer connects to it. If this peer
            // disconnects, the DEALER socket SHALL destroy its double queue and SHALL
            // discard any messages it contains.
            // *** Test disabled until libzmq does this properly ***
            // test_destroy_queue_on_disconnect (ctx);
        }

        ZMQ.term(ctx);
    }

    private void blockOnSendNoPeers(Ctx ctx, String address, int bindType) throws IOException, InterruptedException
    {
        SocketBase dealer = ZMQ.socket(ctx, bindType);

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(dealer, ZMQ.ZMQ_SNDTIMEO, timeout);
        assertThat(rc, is(true));

        int ret = ZMQ.send(dealer, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(dealer.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(dealer, "", 0);
        assertThat(ret, is(-1));
        assertThat(dealer.errno(), is(ZError.EAGAIN));

        rc = ZMQ.bind(dealer, address);
        assertThat(rc, is(true));

        ret = ZMQ.send(dealer, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(dealer.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(dealer, "", 0);
        assertThat(ret, is(-1));
        assertThat(dealer.errno(), is(ZError.EAGAIN));

        ZMQ.close(dealer);
    }

    private void roundRobinOut(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        SocketBase dealer = ZMQ.socket(ctx, bindType);
        boolean rc = ZMQ.bind(dealer, address);
        assertThat(rc, is(true));

        int timeout = 250;
        int services = 5;
        List<SocketBase> senders = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase reps = ZMQ.socket(ctx, connectType);
            assertThat(reps, notNullValue());

            senders.add(reps);

            rc = ZMQ.setSocketOption(reps, ZMQ.ZMQ_RCVTIMEO, timeout);
            assertThat(rc, is(true));

            rc = ZMQ.connect(reps, address);
            assertThat(rc, is(true));
        }

        // Wait for connections.
        ZMQ.msleep(100);

        // Send all requests
        for (int peer = 0; peer < services; ++peer) {
            rc = sendSeq(dealer, null, "ABC");
            assertThat(rc, is(true));
        }

        // Expect every REP got one message
        for (int peer = 0; peer < services; ++peer) {
            recvSeq(senders.get(peer), "ABC");
        }

        ZMQ.closeZeroLinger(dealer);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);
        }

        // Wait for disconnects.
        ZMQ.msleep(100);
    }

    private void fairQueueIn(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        //  Server socket will accept connections
        SocketBase receiver = ZMQ.socket(ctx, bindType);
        assertThat(receiver, notNullValue());

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(receiver, ZMQ.ZMQ_RCVTIMEO, timeout);
        assertThat(rc, is(true));

        rc = ZMQ.bind(receiver, address);
        assertThat(rc, is(true));

        int services = 5;
        List<SocketBase> senders = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase sender = ZMQ.socket(ctx, connectType);
            assertThat(sender, notNullValue());

            senders.add(sender);

            rc = ZMQ.setSocketOption(sender, ZMQ.ZMQ_RCVTIMEO, timeout);
            assertThat(rc, is(true));

            rc = ZMQ.connect(sender, address);
            assertThat(rc, is(true));
        }

        rc = sendSeq(senders.get(0), "A");
        assertThat(rc, is(true));
        recvSeq(receiver, "A");

        rc = sendSeq(senders.get(0), "A");
        assertThat(rc, is(true));
        recvSeq(receiver, "A");

        // send N requests
        for (int peer = 0; peer < services; ++peer) {
            sendSeq(senders.get(peer), "B");
        }

        // Wait for data.
        ZMQ.msleep(50);

        // handle N requests
        for (int peer = 0; peer < services; ++peer) {
            recvSeq(receiver, "B");
        }

        ZMQ.closeZeroLinger(receiver);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);

        }
        // Wait for disconnects.
        ZMQ.msleep(100);
    }
}
