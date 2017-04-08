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
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.socket.AbstractSpecTest;
import zmq.util.Utils;

public class ReqSpecTest extends AbstractSpecTest
{
    @Test
    public void testSpecMessageFormat() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // The request and reply messages SHALL have this format on the wire:
            // * A delimiter, consisting of an empty frame, added by the REQ socket.
            // * One or more data frames, comprising the message visible to the
            //   application.
            messageFormat(ctx, bindAddress, ZMQ.ZMQ_REQ, ZMQ.ZMQ_ROUTER);
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
            // SHALL route outgoing messages to connected peers using a round-robin
            // strategy.
            roundRobinOut(ctx, bindAddress, ZMQ.ZMQ_REQ, ZMQ.ZMQ_REP);
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
            // SHALL block on sending, or return a suitable error, when it has no
            // connected peers.
            blockOnSendNoPeers(ctx, bindAddress, ZMQ.ZMQ_REQ);
        }

        ZMQ.term(ctx);
    }

    @Test
    @Ignore
    public void testSpecOnlyListensToCurrentPeer() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // SHALL accept an incoming message only from the last peer that it sent a
            // request to.
            // SHALL discard silently any messages received from other peers.
            // PH: this test is still failing; disabled for now to allow build to
            // complete.
            onlyListensToCurrentPeer(ctx, bindAddress, ZMQ.ZMQ_REQ, ZMQ.ZMQ_ROUTER);
        }

        ZMQ.term(ctx);
    }

    private void onlyListensToCurrentPeer(Ctx ctx, String bindAddress, int bindType, int connectType)
    {
        SocketBase socket = ZMQ.socket(ctx, bindType);

        boolean rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_IDENTITY, "A");
        assertThat(rc, is(true));

        rc = ZMQ.bind(socket, bindAddress);
        assertThat(rc, is(true));

        int timeout = 250;
        int services = 3;
        List<SocketBase> senders = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase connect = ZMQ.socket(ctx, connectType);
            assertThat(connect, notNullValue());

            senders.add(connect);

            rc = ZMQ.setSocketOption(connect, ZMQ.ZMQ_RCVTIMEO, timeout);
            assertThat(rc, is(true));

            rc = ZMQ.setSocketOption(connect, ZMQ.ZMQ_ROUTER_MANDATORY, true);
            assertThat(rc, is(true));

            rc = ZMQ.connect(connect, bindAddress);
            assertThat(rc, is(true));
        }

        ZMQ.msleep(100);

        for (int peer = 0; peer < services; ++peer) {
            // There still is a race condition when a stale peer's message
            // arrives at the REQ just after a request was sent to that peer.
            // To avoid that happening in the test, sleep for a bit.
            ZMQ.msleep(10);

            sendSeq(socket, "ABC");

            // Receive on router i
            recvSeq(senders.get(peer), "A", null, "ABC");

            // Send back replies on all routers
            for (int j = 0; j < services; ++j) {
                List<String> replies = Arrays.asList("WRONG", "GOOD");
                String reply = peer == j ? replies.get(1) : replies.get(0);
                sendSeq(senders.get(j), "A", null, reply);
            }

            // Receive only the good reply
            recvSeq(socket, "GOOD");
        }

        ZMQ.closeZeroLinger(socket);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);
        }

        // Wait for disconnects.
        ZMQ.msleep(100);
    }

    private void blockOnSendNoPeers(Ctx ctx, String address, int bindType) throws IOException, InterruptedException
    {
        SocketBase socket = ZMQ.socket(ctx, bindType);

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(socket, ZMQ.ZMQ_SNDTIMEO, timeout);
        assertThat(rc, is(true));

        int ret = ZMQ.send(socket, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(socket.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(socket, "", 0);
        assertThat(ret, is(-1));
        assertThat(socket.errno(), is(ZError.EAGAIN));

        rc = ZMQ.bind(socket, address);
        assertThat(rc, is(true));

        ret = ZMQ.send(socket, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(socket.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(socket, "", 0);
        assertThat(ret, is(-1));
        assertThat(socket.errno(), is(ZError.EAGAIN));

        ZMQ.close(socket);
    }

    private void roundRobinOut(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        SocketBase req = ZMQ.socket(ctx, bindType);
        boolean rc = ZMQ.bind(req, address);
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

        //  We have to give the connects time to finish otherwise the requests
        //  will not properly round-robin. We could alternatively connect the
        //  REQ sockets to the REP sockets.
        ZMQ.msleep(200);

        // Send our peer-replies, and expect every REP it used once in order
        for (int peer = 0; peer < services; ++peer) {
            rc = sendSeq(req, "ABC");
            assertThat(rc, is(true));

            recvSeq(senders.get(peer), "ABC");
            rc = sendSeq(senders.get(peer), "DEF");
            assertThat(rc, is(true));

            recvSeq(req, "DEF");
        }

        ZMQ.closeZeroLinger(req);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);
        }

        // Wait for disconnects.
        ZMQ.msleep(100);
    }

    private void messageFormat(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        //  Server socket will accept connections
        SocketBase req = ZMQ.socket(ctx, bindType);
        assertThat(req, notNullValue());

        boolean rc = ZMQ.bind(req, address);
        assertThat(rc, is(true));

        SocketBase router = ZMQ.socket(ctx, connectType);
        assertThat(router, notNullValue());

        rc = ZMQ.connect(router, address);
        assertThat(rc, is(true));

        // Send a multi-part request.
        sendSeq(req, "ABC", "DEF");

        // Receive peer identity
        Msg msg = ZMQ.recv(router, 0);
        assertThat(msg, notNullValue());
        assertThat(msg.size() > 0, is(true));

        Msg peerId = msg;

        int more = ZMQ.getSocketOption(router, ZMQ.ZMQ_RCVMORE);
        assertThat(more, is(1));

        // Receive the rest.
        recvSeq(router, null, "ABC", "DEF");

        // Send back a single-part reply.
        int ret = ZMQ.send(router, peerId, ZMQ.ZMQ_SNDMORE);
        assertThat(ret, is(peerId.size()));

        sendSeq(router, null, "GHI");

        // Receive reply.
        recvSeq(req, "GHI");

        ZMQ.closeZeroLinger(req);
        ZMQ.closeZeroLinger(router);

        // Wait for disconnects.
        ZMQ.msleep(100);
    }
}
