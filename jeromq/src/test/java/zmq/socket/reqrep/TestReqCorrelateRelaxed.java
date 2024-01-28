package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

/**
 * This test does one setup, and runs a few tests on that setup.
 */
public class TestReqCorrelateRelaxed
{
    static final int            REQUEST_ID_LENGTH = 4;
    private static final String PAYLOAD           = "Payload";

    /**
     * Prepares sockets and runs actual tests.
     * <p>
     * Doing it this way so order is guaranteed.
     *
     */
    @Test
    public void overallSetup()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase dealer = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());
        boolean brc = ZMQ.bind(dealer, "inproc://a");
        assertThat(brc, is(true));

        SocketBase reqClient = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(reqClient, notNullValue());

        reqClient.setSocketOpt(ZMQ.ZMQ_REQ_CORRELATE, 1);
        reqClient.setSocketOpt(ZMQ.ZMQ_REQ_RELAXED, 1);
        reqClient.setSocketOpt(ZMQ.ZMQ_RCVTIMEO, 100);

        brc = ZMQ.connect(reqClient, "inproc://a");
        assertThat(brc, is(true));

        // Test good path
        byte[] origRequestId = testReqSentFrames(dealer, reqClient);
        testReqRecvGoodRequestId(dealer, reqClient, origRequestId);

        // Test what happens when a bad request ID is sent back.
        origRequestId = testReqSentFrames(dealer, reqClient);
        testReqRecvBadRequestId(dealer, reqClient, origRequestId);

        ZMQ.close(reqClient);
        ZMQ.close(dealer);
        ZMQ.term(ctx);
    }

    /**
     * Tests that the correct frames are sent by the REQ socket.
     *
     * @param dealer
     * @param reqClient
     * @return the request ID that was received
     */
    public byte[] testReqSentFrames(SocketBase dealer, SocketBase reqClient)
    {
        // Send simple payload over REQ socket
        Msg request = new Msg(PAYLOAD.getBytes());

        assertThat(ZMQ.sendMsg(reqClient, request, 0), is(PAYLOAD.getBytes().length));

        // Verify that the right frames were sent: [request ID, empty frame, payload]
        // 1. request ID
        Msg receivedReqId = ZMQ.recv(dealer, 0);

        assertThat(receivedReqId, notNullValue());
        assertThat(receivedReqId.size(), is(REQUEST_ID_LENGTH));
        assertThat(receivedReqId.flags() & ZMQ.ZMQ_MORE, not(0));

        byte[] buf = new byte[128];
        int requestIdLen = receivedReqId.getBytes(0, buf, 0, 128);
        assertThat(requestIdLen, is(REQUEST_ID_LENGTH));

        byte[] requestId = Arrays.copyOf(buf, REQUEST_ID_LENGTH);

        // 2. empty frame
        Msg receivedEmpty = ZMQ.recv(dealer, 0);

        assertThat(receivedEmpty, notNullValue());
        assertThat(receivedEmpty.size(), is(0));
        assertThat(receivedEmpty.flags() & ZMQ.ZMQ_MORE, not(0));

        // 3. Payload
        Msg receivedPayload = ZMQ.recv(dealer, 0);

        assertThat(receivedPayload, notNullValue());
        assertThat(receivedPayload.size(), is(PAYLOAD.getBytes().length));
        assertThat(receivedPayload.flags() & ZMQ.ZMQ_MORE, is(0));

        int receivedPayloadLen = receivedPayload.getBytes(0, buf, 0, 128);
        assertThat(receivedPayloadLen, is(PAYLOAD.getBytes().length));

        assertThat(Arrays.equals(receivedPayload.data(), PAYLOAD.getBytes()), is(true));

        return requestId;
    }

    /**
     * Test that REQ sockets with CORRELATE/RELAXED receive a response with
     * correct request ID correctly.
     *
     * @param dealer
     * @param reqClient
     * @param origRequestId the request ID that was sent by the REQ socket
     * earlier
     */
    public void testReqRecvGoodRequestId(SocketBase dealer, SocketBase reqClient, byte[] origRequestId)
    {
        Msg requestId = new Msg(origRequestId);
        Msg empty = new Msg();
        Msg responsePayload = new Msg(PAYLOAD.getBytes());

        // Send response
        assertThat(ZMQ.send(dealer, requestId, ZMQ.ZMQ_SNDMORE), is(REQUEST_ID_LENGTH));
        assertThat(ZMQ.send(dealer, empty, ZMQ.ZMQ_SNDMORE), is(0));
        assertThat(ZMQ.send(dealer, responsePayload, 0), is(responsePayload.size()));

        // Receive response (payload only)
        Msg receivedResponsePayload = ZMQ.recv(reqClient, 0);
        assertThat(receivedResponsePayload, notNullValue());

        byte[] buf = new byte[128];
        int payloadLen = receivedResponsePayload.getBytes(0, buf, 0, 128);
        assertThat(payloadLen, is(PAYLOAD.getBytes().length));
    }

    /**
     * Asserts that a response with a non-current request ID is ignored.
     *
     * @param dealer
     * @param reqClient
     * @param origRequestId
     */
    public void testReqRecvBadRequestId(SocketBase dealer, SocketBase reqClient, byte[] origRequestId)
    {
        Msg badRequestId = new Msg("gobbledygook".getBytes());
        Msg goodRequestId = new Msg(origRequestId);

        Msg empty = new Msg();

        Msg badResponsePayload = new Msg("Bad response".getBytes());
        Msg goodResponsePayload = new Msg(PAYLOAD.getBytes());

        // Send response with bad request ID
        assertThat(ZMQ.send(dealer, badRequestId, ZMQ.ZMQ_SNDMORE), is(12));
        assertThat(ZMQ.send(dealer, empty, ZMQ.ZMQ_SNDMORE), is(0));
        assertThat(ZMQ.send(dealer, badResponsePayload, 0), is(badResponsePayload.size()));

        // Send response with good request ID
        assertThat(ZMQ.send(dealer, goodRequestId, ZMQ.ZMQ_SNDMORE), is(REQUEST_ID_LENGTH));
        assertThat(ZMQ.send(dealer, empty, ZMQ.ZMQ_SNDMORE), is(0));
        assertThat(ZMQ.send(dealer, goodResponsePayload, 0), is(goodResponsePayload.size()));

        // Receive response (payload only)
        Msg receivedResponsePayload = ZMQ.recv(reqClient, 0);
        assertThat(receivedResponsePayload, notNullValue());

        // Expecting PAYLOAD, not "Bad payload"
        byte[] buf = new byte[128];
        int payloadLen = receivedResponsePayload.getBytes(0, buf, 0, 128);
        assertThat(payloadLen, is(PAYLOAD.getBytes().length));

        byte[] receivedPayload = Arrays.copyOf(buf, payloadLen);
        assertThat(receivedPayload, is(PAYLOAD.getBytes()));
    }
}
