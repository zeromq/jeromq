package zmq;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//TODO: This class uses O(n) scheduling. Rewrite it to use O(1) algorithm.
public class Router extends SocketBase
{
    public static class RouterSession extends SessionBase
    {
        public RouterSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    //  True iff there is a message held in the pre-fetch buffer.
    private boolean prefetched;

    //  If true, the receiver got the message part with
    //  the peer's identity.
    private boolean identitySent;

    //  Holds the prefetched identity.
    private Msg prefetchedId;

    //  Holds the prefetched message.
    private Msg prefetchedMsg;

    //  If true, more incoming message parts are expected.
    private boolean moreIn;

    class Outpipe
    {
        private Pipe pipe;
        private boolean active;

        public Outpipe(Pipe pipe, boolean active)
        {
            this.pipe = pipe;
            this.active = active;
        }
    };

    //  We keep a set of pipes that have not been identified yet.
    private final Set<Pipe> anonymousPipes;

    //  Outbound pipes indexed by the peer IDs.
    private final Map<Blob, Outpipe> outpipes;

    //  The pipe we are currently writing to.
    private Pipe currentOut;

    //  If true, more outgoing message parts are expected.
    private boolean moreOut;

    //  Peer ID are generated. It's a simple increment and wrap-over
    //  algorithm. This value is the next ID to use (if not used already).
    private int nextPeerId;

    // If true, report EHOSTUNREACH to the caller instead of silently dropping
    // the message targeting an unknown peer.
    private boolean mandatory;

    private boolean handover;

    public Router(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        prefetched = false;
        identitySent = false;
        moreIn = false;
        currentOut = null;
        moreOut = false;
        nextPeerId = Utils.generateRandom();
        mandatory = false;
        handover = false;

        options.type = ZMQ.ZMQ_ROUTER;

        fq = new FQ();
        prefetchedId = new Msg();
        prefetchedMsg = new Msg();

        anonymousPipes = new HashSet<Pipe>();
        outpipes = new HashMap<Blob, Outpipe>();

        //  TODO: Uncomment the following line when ROUTER will become true ROUTER
        //  rather than generic router socket.
        //  If peer disconnect there's noone to send reply to anyway. We can drop
        //  all the outstanding requests from that peer.
        //  options.delayOnDisconnect = false;

        options.recvIdentity = true;
    }

    @Override
    public void xattachPipe(Pipe pipe, boolean icanhasall)
    {
        assert (pipe != null);

        boolean identityOk = identifyPeer(pipe);
        if (identityOk) {
            fq.attach(pipe);
        }
        else {
            anonymousPipes.add(pipe);
        }
    }

    @Override
    public boolean xsetsockopt(int option, Object optval)
    {
        if (option == ZMQ.ZMQ_ROUTER_MANDATORY) {
            mandatory = (Integer) optval == 1;
            return true;
        }
        if (option == ZMQ.ZMQ_ROUTER_HANDOVER) {
            handover = (Integer) optval == 1;
            return true;
        }
        return false;
    }

    @Override
    public void xpipeTerminated(Pipe pipe)
    {
        if (!anonymousPipes.remove(pipe)) {
            Outpipe old = outpipes.remove(pipe.getIdentity());
            assert (old != null);

            fq.terminated(pipe);
            if (pipe == currentOut) {
                currentOut = null;
            }
        }
    }

    @Override
    public void xreadActivated(Pipe pipe)
    {
        if (!anonymousPipes.contains(pipe)) {
            fq.activated(pipe);
        }
        else {
            boolean identityOk = identifyPeer(pipe);
            if (identityOk) {
                anonymousPipes.remove(pipe);
                fq.attach(pipe);
            }
        }
    }

    @Override
    public void xwriteActivated(Pipe pipe)
    {
        for (Map.Entry<Blob, Outpipe> it : outpipes.entrySet()) {
            if (it.getValue().pipe == pipe) {
                assert (!it.getValue().active);
                it.getValue().active = true;
                return;
            }
        }
        assert (false);
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  If this is the first part of the message it's the ID of the
        //  peer to send the message to.
        if (!moreOut) {
            assert (currentOut == null);

            //  If we have malformed message (prefix with no subsequent message)
            //  then just silently ignore it.
            //  TODO: The connections should be killed instead.
            if (msg.hasMore()) {
                moreOut = true;

                //  Find the pipe associated with the identity stored in the prefix.
                //  If there's no such pipe just silently ignore the message, unless
                //  mandatory is set.
                Blob identity = Blob.createBlob(msg.data(), true);
                Outpipe op = outpipes.get(identity);

                if (op != null) {
                    currentOut = op.pipe;
                    if (!currentOut.checkWrite()) {
                        op.active = false;
                        currentOut = null;
                        if (mandatory) {
                            moreOut = false;
                            errno.set(ZError.EAGAIN);
                            return false;
                        }
                    }
                }
                else if (mandatory) {
                    moreOut = false;
                    errno.set(ZError.EHOSTUNREACH);
                    return false;
                }
            }

            return true;
        }

        //  Check whether this is the last part of the message.
        moreOut = msg.hasMore();

        //  Push the message into the pipe. If there's no out pipe, just drop it.
        if (currentOut != null) {
            boolean ok = currentOut.write(msg);
            if (!ok) {
                currentOut = null;
            }
            else if (!moreOut) {
                currentOut.flush();
                currentOut = null;
            }
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        Msg msg = null;
        if (prefetched) {
            if (!identitySent) {
                msg = prefetchedId;
                prefetchedId = null;
                identitySent = true;
            }
            else {
                msg = prefetchedMsg;
                prefetchedMsg = null;
                prefetched = false;
            }
            moreIn = msg.hasMore();
            return msg;
        }

        ValueReference<Pipe> pipe = new ValueReference<Pipe>();
        msg = fq.recvPipe(errno, pipe);

        //  It's possible that we receive peer's identity. That happens
        //  after reconnection. The current implementation assumes that
        //  the peer always uses the same identity.
        //  TODO: handle the situation when the peer changes its identity.
        while (msg != null && msg.isIdentity()) {
            msg = fq.recvPipe(errno, pipe);
        }

        if (msg == null) {
            return null;
        }

        assert (pipe.get() != null);

        //  If we are in the middle of reading a message, just return the next part.
        if (moreIn) {
            moreIn = msg.hasMore();
        }
        else {
            //  We are at the beginning of a message.
            //  Keep the message part we have in the prefetch buffer
            //  and return the ID of the peer instead.
            prefetchedMsg = msg;
            prefetched = true;

            Blob identity = pipe.get().getIdentity();
            msg = new Msg(identity.data());
            msg.setFlags(Msg.MORE);
            identitySent = true;
        }

        return msg;
    }

    //  Rollback any message parts that were sent but not yet flushed.
    protected void rollback()
    {
        if (currentOut != null) {
            currentOut.rollback();
            currentOut = null;
            moreOut = false;
        }
    }

    @Override
    protected boolean xhasIn()
    {
        //  If we are in the middle of reading the messages, there are
        //  definitely more parts available.
        if (moreIn) {
            return true;
        }

        //  We may already have a message pre-fetched.
        if (prefetched) {
            return true;
        }

        //  Try to read the next message.
        //  The message, if read, is kept in the pre-fetch buffer.
        ValueReference<Pipe> pipe = new ValueReference<Pipe>();
        prefetchedMsg = fq.recvPipe(errno, pipe);

        //  It's possible that we receive peer's identity. That happens
        //  after reconnection. The current implementation assumes that
        //  the peer always uses the same identity.
        //  TODO: handle the situation when the peer changes its identity.
        while (prefetchedMsg != null && prefetchedMsg.isIdentity()) {
            prefetchedMsg = fq.recvPipe(errno, pipe);
        }

        if (prefetchedMsg == null) {
            return false;
        }

        assert (pipe.get() != null);

        Blob identity = pipe.get().getIdentity();
        prefetchedId = new Msg(identity.data());
        prefetchedId.setFlags(Msg.MORE);

        prefetched = true;
        identitySent = false;

        return true;
    }

    @Override
    protected boolean xhasOut()
    {
        //  In theory, ROUTER socket is always ready for writing. Whether actual
        //  attempt to write succeeds depends on whitch pipe the message is going
        //  to be routed to.
        return true;
    }

    private boolean identifyPeer(Pipe pipe)
    {
        Blob identity;

        Msg msg = pipe.read();
        if (msg == null) {
            return false;
        }

        if (msg.size() == 0) {
            //  Fall back on the auto-generation
            ByteBuffer buf = ByteBuffer.allocate(5);
            buf.put((byte) 0);
            buf.putInt(nextPeerId++);
            identity = Blob.createBlob(buf.array(), false);
        }
        else {
            identity = Blob.createBlob(msg.data(), true);

            if (outpipes.containsKey(identity)) {
                if (!handover) {
                    return false;
                }
                //  We will allow the new connection to take over this
                //  identity. Temporarily assign a new identity to the
                //  existing pipe so we can terminate it asynchronously.
                ByteBuffer buf = ByteBuffer.allocate(5);
                buf.put((byte) 0);
                buf.putInt(nextPeerId++);
                Blob newIdentity = Blob.createBlob(buf.array(), false);

                //  Remove the existing identity entry to allow the new
                //  connection to take the identity.
                Outpipe existingOutpipe = outpipes.remove(identity);
                existingOutpipe.pipe.setIdentity(newIdentity);

                outpipes.put(newIdentity, existingOutpipe);

                existingOutpipe.pipe.terminate(true);
            }
        }

        pipe.setIdentity(identity);
        //  Add the record into output pipes lookup table
        Outpipe outpipe = new Outpipe(pipe, true);
        outpipes.put(identity, outpipe);

        return true;
    }
}
