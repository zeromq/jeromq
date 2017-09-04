package zmq.socket;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.Metadata;
import zmq.pipe.Pipe;
import zmq.util.Blob;
import zmq.util.Utils;
import zmq.util.ValueReference;
import zmq.util.Wire;

public class Stream extends SocketBase
{
    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    //  True if there is a message held in the pre-fetch buffer.
    private boolean prefetched;

    //  If true, the receiver got the message part with
    //  the peer's identity.
    private boolean identitySent;

    //  Holds the prefetched identity.
    private Msg prefetchedId;

    //  Holds the prefetched message.
    private Msg prefetchedMsg;

    private class Outpipe
    {
        private Pipe    pipe;
        private boolean active;

        public Outpipe(Pipe pipe, boolean active)
        {
            this.pipe = pipe;
            this.active = active;
        }
    }

    //  Outbound pipes indexed by the peer IDs.
    private Map<Blob, Outpipe> outpipes = new HashMap<>();

    //  The pipe we are currently writing to.
    private Pipe currentOut;

    //  If true, more outgoing message parts are expected.
    private boolean moreOut;

    //  Routing IDs are generated. It's a simple increment and wrap-over
    //  algorithm. This value is the next ID to use (if not used already).
    private int nextRid;

    public Stream(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        prefetched = false;
        identitySent = false;
        currentOut = null;
        moreOut = false;
        nextRid = Utils.randomInt();
        options.type = ZMQ.ZMQ_STREAM;
        options.rawSocket = true;

        fq = new FQ();
        prefetchedId = new Msg();
        prefetchedMsg = new Msg();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
    {
        assert (pipe != null);

        identifyPeer(pipe);
        fq.attach(pipe);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        Outpipe outpipe = outpipes.remove(pipe.getIdentity());
        assert (outpipe != null);
        fq.terminated(pipe);
        if (pipe == currentOut) {
            currentOut = null;
        }
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        fq.activated(pipe);
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        Outpipe out = null;
        for (Outpipe outpipe : outpipes.values()) {
            if (outpipe.pipe == pipe) {
                out = outpipe;
                break;
            }
        }
        assert (out != null);
        assert (!out.active);
        out.active = true;
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
                //  If there's no such pipe return an error
                Blob identity = Blob.createBlob(msg);
                Outpipe op = outpipes.get(identity);

                if (op != null) {
                    currentOut = op.pipe;
                    if (!currentOut.checkWrite()) {
                        op.active = false;
                        currentOut = null;
                        errno.set(ZError.EAGAIN);
                        return false;
                    }
                }
                else {
                    errno.set(ZError.EHOSTUNREACH);
                    return false;
                }
            }
            //  Expect one more message frame.
            moreOut = true;

            return true;
        }

        //  Ignore the MORE flag
        msg.resetFlags(Msg.MORE);

        //  This is the last part of the message.
        moreOut = false;

        //  Push the message into the pipe. If there's no out pipe, just drop it.
        if (currentOut != null) {
            // Close the remote connection if user has asked to do so
            // by sending zero length message.
            // Pending messages in the pipe will be dropped (on receiving term- ack)
            if (msg.size() == 0) {
                currentOut.terminate(false);
                currentOut = null;
                return true;
            }
            boolean ok = currentOut.write(msg);
            if (ok) {
                currentOut.flush();
            }
            currentOut = null;
        }

        return true;
    }

    @Override
    protected boolean xsetsockopt(int option, Object optval)
    {
        switch (option) {
        case ZMQ.ZMQ_CONNECT_RID:
            connectRid = (String) optval;
            return true;
        default:
            errno.set(ZError.EINVAL);
            return false;
        }
    }

    @Override
    public Msg xrecv()
    {
        Msg msg;
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
            return msg;
        }

        ValueReference<Pipe> pipe = new ValueReference<>();
        prefetchedMsg = fq.recvPipe(errno, pipe);

        // TODO DIFF V4 we sometimes need to process the commands to receive data, let's just return and give it another chance
        if (prefetchedMsg == null) {
            errno.set(ZError.EAGAIN);
            return null;
        }
        assert (pipe.get() != null);
        assert (!prefetchedMsg.hasMore());

        //  We have received a frame with TCP data.
        //  Rather than sending this frame, we keep it in prefetched
        //  buffer and send a frame with peer's ID.
        Blob identity = pipe.get().getIdentity();

        msg = new Msg(identity.data());

        // forward metadata (if any)
        Metadata metadata = prefetchedMsg.getMetadata();
        if (metadata != null) {
            msg.setMetadata(metadata);
        }

        msg.setFlags(Msg.MORE);

        prefetched = true;
        identitySent = true;

        return msg;
    }

    @Override
    protected boolean xhasIn()
    {
        //  We may already have a message pre-fetched.
        if (prefetched) {
            return true;
        }

        //  Try to read the next message.
        //  The message, if read, is kept in the pre-fetch buffer.
        ValueReference<Pipe> pipe = new ValueReference<>();
        prefetchedMsg = fq.recvPipe(errno, pipe);
        if (prefetchedMsg == null) {
            return false;
        }

        assert (pipe.get() != null);
        assert (!prefetchedMsg.hasMore());

        Blob identity = pipe.get().getIdentity();

        prefetchedId = new Msg(identity.data());

        // forward metadata (if any)
        Metadata metadata = prefetchedMsg.getMetadata();
        if (metadata != null) {
            prefetchedId.setMetadata(metadata);
        }

        prefetchedId.setFlags(Msg.MORE);

        prefetched = true;
        identitySent = false;

        return true;
    }

    @Override
    protected boolean xhasOut()
    {
        //  In theory, STREAM socket is always ready for writing. Whether actual
        //  attempt to write succeeds depends on which pipe the message is going
        //  to be routed to.
        return true;
    }

    private void identifyPeer(Pipe pipe)
    {
        //  Always assign identity for raw-socket

        Blob identity;
        if (connectRid != null && !connectRid.isEmpty()) {
            identity = Blob.createBlob(connectRid.getBytes(ZMQ.CHARSET));
            connectRid = null;

            Outpipe outpipe = outpipes.get(identity);
            assert (outpipe == null);
        }
        else {
            ByteBuffer buf = ByteBuffer.allocate(5);
            buf.put((byte) 0);
            Wire.putUInt32(buf, nextRid++);
            identity = Blob.createBlob(buf.array());
        }
        pipe.setIdentity(identity);
        //  Add the record into output pipes lookup table
        Outpipe outpipe = new Outpipe(pipe, true);
        outpipes.put(identity, outpipe);
    }
}
