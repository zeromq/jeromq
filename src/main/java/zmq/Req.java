package zmq;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;

public class Req extends Dealer
{
    static final int REQUEST_ID_LENGTH = 6;
    // Random generator for request IDs.
    public static final Random REQUEST_ID_GEN = new Random(
            Calendar.getInstance().getTimeInMillis());

    //  If true, request was already sent and reply wasn't received yet or
    //  was raceived partially.
    private boolean receivingReply;

    // If ZMQ_REQ_CORRELATE is enabled, this is set to the request ID of the most
    // recent request. Any incoming message that does not have this ID will
    // be discarded.
    private byte[] currentRequestId;

    //  If true, we are starting to send/recv a message. The first part
    //  of the message must be empty message part (backtrace stack bottom).
    private boolean messageBegins;

    public Req(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        receivingReply = false;
        messageBegins = true;
        options.type = ZMQ.ZMQ_REQ;
        currentRequestId = new byte[REQUEST_ID_LENGTH];
    }

    @Override
    public boolean xsend(Msg msg)
    {
        //  If we've sent a request and we still haven't got the reply,
        //  we can't send another request.
        if (receivingReply && getSocketOpt(zmq.ZMQ.ZMQ_REQ_RELAXED) == 0) {
            errno.set(ZError.EFSM);
            return false;
        }

        //  First part of the request is the request identity.
        if (messageBegins) {
            // If CORRELATE is enabled, send request ID frame before the empty
            // frame.
            if (getSocketOpt(zmq.ZMQ.ZMQ_REQ_CORRELATE) > 0) {
                REQUEST_ID_GEN.nextBytes(currentRequestId);

                Msg requestId = new Msg(currentRequestId);
                requestId.setFlags(Msg.MORE);

                boolean rc = super.xsend(requestId);
                if (!rc) {
                    return rc;
                }
            }

            Msg bottom = new Msg();

            bottom.setFlags(Msg.MORE);
            boolean rc = super.xsend(bottom);
            if (!rc) {
                return rc;
            }
            messageBegins = false;
        }

        boolean more = msg.hasMore();

        boolean rc = super.xsend(msg);
        if (!rc) {
            return rc;
        }

        //  If the request was fully sent, flip the FSM into reply-receiving state.
        if (!more) {
            receivingReply = true;
            messageBegins = true;
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        // If request wasn't send, we can't wait for reply.
        // Thus, we don't look at the state of the ZMQ_REQ_RELAXED option.
        if (!receivingReply) {
            errno.set(ZError.EFSM);
            return null;
        }

        Msg msg = null;
        //  First part of the reply should be the original request ID, if
        // ZMQ_REQ_CORRELATE is enabled.
        if (messageBegins) {
            msg = super.xrecv();
            if (msg == null) {
                return null;
            }

            boolean requestIdIsBad = false;

            // Check request ID
            if (getSocketOpt(zmq.ZMQ.ZMQ_REQ_CORRELATE) > 0) {
                requestIdIsBad = !Arrays.equals(msg.data(), currentRequestId);

                // Receive empty delimiter frame
                msg = super.xrecv();
                if (msg == null) {
                    return null;
                }
            }

            // TODO: This should also close the connection with the peer!
            if (!msg.hasMore() || msg.size() != 0 || requestIdIsBad) {
                while (true) {
                    msg = super.xrecv();
                    assert (msg != null);
                    if (!msg.hasMore()) {
                        break;
                    }
                }
                errno.set(ZError.EAGAIN);
                return null;
            }

            messageBegins = false;
        }

        msg = super.xrecv();
        if (msg == null) {
            return null;
        }

        //  If the reply is fully received, flip the FSM into request-sending state.
        if (!msg.hasMore()) {
            receivingReply = false;
            messageBegins = true;
        }

        return msg;
    }

    @Override
    public boolean xhasIn()
    {
        //  TODO: Duplicates should be removed here.

        return receivingReply && super.xhasIn();
    }

    @Override
    public boolean xhasOut()
    {
        return !receivingReply && super.xhasOut();
    }

    public static class ReqSession extends Dealer.DealerSession
    {
        enum State {
            IDENTITY,
            BOTTOM,
            BODY
        };

        private State state;

        public ReqSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);

            state = State.IDENTITY;
        }

        @Override
        public int pushMsg(Msg msg)
        {
            switch (state) {
            case BOTTOM:
                if (msg.hasMore() && msg.size() == 0) {
                    state = State.BODY;
                    return super.pushMsg(msg);
                }
                break;
            case BODY:
                if (msg.hasMore()) {
                    return super.pushMsg(msg);
                }
                if (msg.flags() == 0) {
                    state = State.BOTTOM;
                    return super.pushMsg(msg);
                }
                break;
            case IDENTITY:
                if (msg.flags() == 0) {
                    state = State.BOTTOM;
                    return super.pushMsg(msg);
                }
                break;
            }
            socket.errno.set(ZError.EFAULT);
            return -1;
        }

        public void reset()
        {
            super.reset();
            state = State.IDENTITY;
        }
    }
}
