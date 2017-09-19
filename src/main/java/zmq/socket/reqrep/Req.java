package zmq.socket.reqrep;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.pipe.Pipe;
import zmq.util.Utils;
import zmq.util.ValueReference;
import zmq.util.Wire;

public class Req extends Dealer
{
    //  If true, request was already sent and reply wasn't received yet or
    //  was received partially.
    private boolean receivingReply;

    //  If true, we are starting to send/recv a message. The first part
    //  of the message must be empty message part (backtrace stack bottom).
    private boolean messageBegins;

    //  The pipe the request was sent to and where the reply is expected.
    private final ValueReference<Pipe> replyPipe = new ValueReference<>();

    //  Whether request id frames shall be sent and expected.
    private boolean requestIdFramesEnabled;

    //  The current request id. It is incremented every time before a new
    //  request is sent.
    private int requestId;

    //  If false, send() will reset its internal state and terminate the
    //  reply_pipe's connection instead of failing if a previous request is
    //  still pending.
    private boolean strict;

    public Req(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        receivingReply = false;
        messageBegins = true;
        options.type = ZMQ.ZMQ_REQ;
        requestIdFramesEnabled = false;
        requestId = Utils.randomInt();
        strict = true;
    }

    @Override
    public boolean xsend(final Msg msg)
    {
        //  If we've sent a request and we still haven't got the reply,
        //  we can't send another request.
        if (receivingReply) {
            if (strict) {
                errno.set(ZError.EFSM);
                return false;
            }

            receivingReply = false;
            messageBegins = true;
        }

        //  First part of the request is the request identity.
        if (messageBegins) {
            replyPipe.set(null);

            if (requestIdFramesEnabled) {
                requestId++;

                final Msg id = new Msg(4);
                Wire.putUInt32(id.buf(), requestId);
                id.setFlags(Msg.MORE);
                boolean rc = super.sendpipe(id, replyPipe);
                if (!rc) {
                    return false;
                }

            }
            Msg bottom = new Msg();
            bottom.setFlags(Msg.MORE);
            boolean rc = super.sendpipe(bottom, replyPipe);
            if (!rc) {
                return false;
            }
            assert (replyPipe.get() != null);

            messageBegins = false;

            // Eat all currently available messages before the request is fully
            // sent. This is done to avoid:
            //   REQ sends request to A, A replies, B replies too.
            //   A's reply was first and matches, that is used.
            //   An hour later REQ sends a request to B. B's old reply is used.
            while (true) {
                Msg drop = super.xrecv();
                if (drop == null) {
                    break;
                }
            }
        }

        boolean more = msg.hasMore();

        boolean rc = super.xsend(msg);
        if (!rc) {
            return false;
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

        //  Skip messages until one with the right first frames is found.
        while (messageBegins) {
            //  If enabled, the first frame must have the correct request_id.
            if (requestIdFramesEnabled) {
                Msg msg = recvReplyPipe();
                if (msg == null) {
                    return null;
                }
                if (!msg.hasMore() || msg.size() != 4 || Wire.getUInt32(msg, 0) != requestId) {
                    //  Skip the remaining frames and try the next message
                    while (msg.hasMore()) {
                        msg = recvReplyPipe();
                        assert (msg != null);
                    }
                    continue;
                }
            }

            //  The next frame must be 0.
            // TODO: Failing this check should also close the connection with the peer!
            Msg msg = recvReplyPipe();
            if (msg == null) {
                return null;
            }
            if (!msg.hasMore() || msg.size() != 0) {
                //  Skip the remaining frames and try the next message
                while (msg.hasMore()) {
                    msg = recvReplyPipe();
                    assert (msg != null);
                }
                continue;
            }

            messageBegins = false;
        }

        Msg msg = recvReplyPipe();
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

    @Override
    protected boolean xsetsockopt(int option, Object optval)
    {
        switch (option) {
        case ZMQ.ZMQ_REQ_CORRELATE:
            requestIdFramesEnabled = Options.parseBoolean(option, optval);
            return true;
        case ZMQ.ZMQ_REQ_RELAXED:
            strict = !Options.parseBoolean(option, optval);
            return true;

        default:
            break;
        }
        return super.xsetsockopt(option, optval);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        if (replyPipe.get() == pipe) {
            replyPipe.set(null);
        }
        super.xpipeTerminated(pipe);
    }

    private Msg recvReplyPipe()
    {
        while (true) {
            ValueReference<Pipe> pipe = new ValueReference<>();
            Msg msg = super.recvpipe(pipe);
            if (msg == null) {
                return null;
            }

            if (replyPipe.get() == null || replyPipe.get() == pipe.get()) {
                return msg;
            }
        }
    }

    public static class ReqSession extends SessionBase
    {
        enum State
        {
            BOTTOM,
            BODY
        }

        private State state;

        public ReqSession(IOThread ioThread, boolean connect, SocketBase socket, final Options options,
                final Address addr)
        {
            super(ioThread, connect, socket, options, addr);

            state = State.BOTTOM;
        }

        @Override
        public boolean pushMsg(Msg msg)
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
            default:
                break;
            }
            errno.set(ZError.EFAULT);
            return false;
        }

        @Override
        public void reset()
        {
            super.reset();
            state = State.BOTTOM;
        }
    }
}
