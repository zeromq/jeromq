package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.socket.AbstractSpecTest;
import zmq.util.Utils;

public class RepSpecTest extends AbstractSpecTest
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
            fairQueueIn(ctx, bindAddress, ZMQ.ZMQ_REP, ZMQ.ZMQ_REQ);
        }

        ZMQ.term(ctx);
    }

    @Test
    public void testSpecEnvelope() throws IOException, InterruptedException
    {
        Ctx ctx = ZMQ.createContext();
        int port = Utils.findOpenPort();
        List<String> binds = Arrays.asList("inproc://a", "tcp://127.0.0.1:" + port);

        for (String bindAddress : binds) {
            // For an incoming message:
            // SHALL remove and store the address envelope, including the delimiter.
            // SHALL pass the remaining data frames to its calling application.
            // SHALL wait for a single reply message from its calling application.
            // SHALL prepend the address envelope and delimiter.
            // SHALL deliver this message back to the originating peer.
            envelope(ctx, bindAddress, ZMQ.ZMQ_REP, ZMQ.ZMQ_DEALER);
        }

        ZMQ.term(ctx);
    }

    private void envelope(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        SocketBase rep = ZMQ.socket(ctx, bindType);
        boolean rc = ZMQ.bind(rep, address);
        assertThat(rc, is(true));

        SocketBase dealer = ZMQ.socket(ctx, connectType);
        assertThat(dealer, notNullValue());

        rc = ZMQ.connect(dealer, address);
        assertThat(rc, is(true));

        // minimal envelope
        sendSeq(dealer, null, "A");
        recvSeq(rep, "A");
        sendSeq(rep, "A");
        recvSeq(dealer, null, "A");

        // big envelope
        sendSeq(dealer, "X", "Y", null, "A");
        recvSeq(rep, "A");
        sendSeq(rep, "A");
        recvSeq(dealer, "X", "Y", null, "A");

        ZMQ.closeZeroLinger(rep);
        ZMQ.closeZeroLinger(dealer);

        // Wait for disconnects.
        ZMQ.msleep(100);
    }

    private void fairQueueIn(Ctx ctx, String address, int bindType, int connectType)
            throws IOException, InterruptedException
    {
        //  Server socket will accept connections
        SocketBase rep = ZMQ.socket(ctx, bindType);
        assertThat(rep, notNullValue());

        int timeout = 250;
        boolean rc = ZMQ.setSocketOption(rep, ZMQ.ZMQ_RCVTIMEO, timeout);
        assertThat(rc, is(true));

        rc = ZMQ.bind(rep, address);
        assertThat(rc, is(true));

        int services = 5;
        List<SocketBase> reqs = new ArrayList<>();
        for (int peer = 0; peer < services; ++peer) {
            SocketBase sender = ZMQ.socket(ctx, connectType);
            assertThat(sender, notNullValue());

            reqs.add(sender);

            rc = ZMQ.setSocketOption(sender, ZMQ.ZMQ_RCVTIMEO, timeout);
            assertThat(rc, is(true));

            rc = ZMQ.connect(sender, address);
            assertThat(rc, is(true));
        }

        rc = sendSeq(reqs.get(0), "A");
        assertThat(rc, is(true));
        recvSeq(rep, "A");
        rc = sendSeq(rep, "A");
        assertThat(rc, is(true));
        recvSeq(reqs.get(0), "A");

        rc = sendSeq(reqs.get(0), "A");
        assertThat(rc, is(true));
        recvSeq(rep, "A");
        rc = sendSeq(rep, "A");
        assertThat(rc, is(true));
        recvSeq(reqs.get(0), "A");

        boolean someoneFixThis = false;
        // TODO V4 review this test (breaking in libzmq): there is no guarantee about the order of the replies.
        if (someoneFixThis) {
            // send N requests
            for (int peer = 0; peer < services; ++peer) {
                sendSeq(reqs.get(peer), "B" + peer);
            }

            Set<String> replies = new HashSet<>();
            // handle N requests
            for (int peer = 0; peer < services; ++peer) {
                Msg msg = ZMQ.recv(rep, 0);
                assertThat(msg, notNullValue());

                String reply = new String(msg.data(), ZMQ.CHARSET);
                replies.add(reply);
                sendSeq(rep, reply);
            }
            for (int peer = 0; peer < services; ++peer) {
                Msg msg = ZMQ.recv(reqs.get(peer), 0);
                assertThat(msg, notNullValue());

                String reply = new String(msg.data(), ZMQ.CHARSET);
                replies.remove(reply);
            }
            assertThat(replies.size(), is(0));
        }

        ZMQ.closeZeroLinger(rep);
        for (SocketBase sender : reqs) {
            ZMQ.closeZeroLinger(sender);

        }
        // Wait for disconnects.
        ZMQ.msleep(100);
    }
}
