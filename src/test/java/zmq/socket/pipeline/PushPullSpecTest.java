package zmq.socket.pipeline;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
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
import zmq.ZError;
import zmq.ZMQ;
import zmq.socket.AbstractSpecTest;
import zmq.util.Utils;

public class PushPullSpecTest extends AbstractSpecTest
{
    @Test
    public void testSpecPullFairQueueIn() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // PULL: SHALL receive incoming messages from its peers using a fair-queuing
            // strategy.
            fairQueueIn(ctx, bindAddress, ZMQ.ZMQ_PULL, ZMQ.ZMQ_PUSH);
        }

        ZMQ.term(ctx);
    }

    @Test
    public void testSpecPushRoundRobinOut() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // PUSH: SHALL route outgoing messages to connected peers using a
            // round-robin strategy.
            roundRobinOut(ctx, bindAddress, ZMQ.ZMQ_PUSH, ZMQ.ZMQ_PULL);
        }

        ZMQ.term(ctx);
    }

    @Test
    public void testSpecPushBlockOnSendNoPeers() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // PUSH: SHALL block on sending, or return a suitable error, when it has no
            // available peers.
            blockOnSendNoPeers(ctx, bindAddress, ZMQ.ZMQ_PUSH);
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
            // PUSH and PULL: SHALL create this queue when a peer connects to it. If
            // this peer disconnects, the socket SHALL destroy its queue and SHALL
            // discard any messages it contains.
            // *** Test disabled until libzmq does this properly ***
        }

        ZMQ.term(ctx);
    }

    private void blockOnSendNoPeers(Ctx ctx, String address, int bindType) throws IOException, InterruptedException
    {
        SocketBase push = ZMQ.socket(ctx, bindType);

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(push, ZMQ.ZMQ_SNDTIMEO, timeout);
        assertThat(rc, is(true));

        int ret = ZMQ.send(push, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(push.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(push, "", 0);
        assertThat(ret, is(-1));
        assertThat(push.errno(), is(ZError.EAGAIN));

        rc = ZMQ.bind(push, address);
        assertThat(rc, is(true));

        ret = ZMQ.send(push, "", ZMQ.ZMQ_DONTWAIT);
        assertThat(ret, is(-1));
        assertThat(push.errno(), is(ZError.EAGAIN));

        ret = ZMQ.send(push, "", 0);
        assertThat(ret, is(-1));
        assertThat(push.errno(), is(ZError.EAGAIN));

        ZMQ.close(push);
    }

    private void roundRobinOut(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        SocketBase push = ZMQ.socket(ctx, bindType);
        boolean rc = ZMQ.bind(push, address);
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

        // Send 2N messages
        for (int peer = 0; peer < services; ++peer) {
            rc = sendSeq(push, "ABC");
            assertThat(rc, is(true));
        }
        for (int peer = 0; peer < services; ++peer) {
            rc = sendSeq(push, "DEF");
            assertThat(rc, is(true));
        }

        // Expect every PULL got one of each
        for (int peer = 0; peer < services; ++peer) {
            recvSeq(senders.get(peer), "ABC");
            recvSeq(senders.get(peer), "DEF");
        }

        ZMQ.closeZeroLinger(push);
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
        SocketBase pull = ZMQ.socket(ctx, bindType);
        assertThat(pull, notNullValue());

        boolean rc = ZMQ.bind(pull, address);
        assertThat(rc, is(true));

        int services = 5;
        List<SocketBase> senders = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase sender = ZMQ.socket(ctx, connectType);
            assertThat(sender, notNullValue());

            senders.add(sender);

            rc = ZMQ.connect(sender, address);
            assertThat(rc, is(true));
        }

        // Wait for connections.
        ZMQ.msleep(100);

        Set<String> firstHalf = new HashSet<>();
        Set<String> secondHalf = new HashSet<>();

        // Send 2N messages
        for (int peer = 0; peer < services; ++peer) {
            sendSeq(senders.get(peer), "A" + peer);
            firstHalf.add("A" + peer);

            sendSeq(senders.get(peer), "B" + peer);
            secondHalf.add("B" + peer);
        }

        // Wait for data.
        ZMQ.msleep(100);

        // Expect to pull one from each first
        for (int peer = 0; peer < services; ++peer) {
            Msg msg = ZMQ.recv(pull, 0);
            assertThat(msg, notNullValue());
            assertThat(msg.size(), is(2));
            firstHalf.remove(new String(msg.data(), ZMQ.CHARSET));
        }
        assertThat(firstHalf.size(), is(0));

        // And then get the second batch
        for (int peer = 0; peer < services; ++peer) {
            Msg msg = ZMQ.recv(pull, 0);
            assertThat(msg, notNullValue());
            assertThat(msg.size(), is(2));
            secondHalf.remove(new String(msg.data(), ZMQ.CHARSET));
        }
        assertThat(secondHalf.size(), is(0));

        ZMQ.closeZeroLinger(pull);
        for (SocketBase sender : senders) {
            ZMQ.closeZeroLinger(sender);

        }
        // Wait for disconnects.
        ZMQ.msleep(100);
    }
}
