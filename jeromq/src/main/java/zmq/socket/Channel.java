package zmq.socket;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.util.Blob;

public class Channel extends SocketBase
{
    private Pipe pipe;
    private Pipe lastIn;

    private Blob savedCredential;

    public Channel(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);
        options.type = ZMQ.ZMQ_CHANNEL;
    }

    @Override
    protected void destroy()
    {
        super.destroy();
        assert (pipe == null);
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        //  ZMQ_PAIR socket can only be connected to a single peer.
        //  The socket rejects any further connection requests.
        if (this.pipe == null) {
            this.pipe = pipe;
        }
        else {
            pipe.terminate(false);
        }
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        if (this.pipe == pipe) {
            if (lastIn == pipe) {
                savedCredential = lastIn.getCredential();
                lastIn = null;
            }
            this.pipe = null;
        }
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        //  There's just one pipe. No lists of active and inactive pipes.
        //  There's nothing to do here.
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        //  There's just one pipe. No lists of active and inactive pipes.
        //  There's nothing to do here.
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  CHANNEL sockets do not allow multipart data (ZMQ_SNDMORE)
        if (msg.hasMore()) {
            errno.set(ZError.EINVAL);
            return false;
        }

        if (pipe == null || !pipe.write(msg)) {
            errno.set(ZError.EAGAIN);
            return false;
        }

        pipe.flush();

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        if (pipe == null) {
            //  Initialize the output parameter to be a 0-byte message.
            errno.set(ZError.EAGAIN);
            return null;
        }

        // Drop any messages with more flag
        Msg msg = pipe.read();
        while (msg != null && msg.hasMore()) {
            // drop all frames of the current multi-frame message
            msg = pipe.read();

            while (msg != null && msg.hasMore()) {
                msg = pipe.read();
            }

            // get the new message
            if (msg != null) {
                msg = pipe.read();
            }
        }

        if (msg == null) {
            errno.set(ZError.EAGAIN);
            return null;
        }

        lastIn = pipe;
        return msg;
    }

    @Override
    protected boolean xhasIn()
    {
        return pipe != null && pipe.checkRead();
    }

    @Override
    protected boolean xhasOut()
    {
        return pipe != null && pipe.checkWrite();
    }

    @Override
    protected Blob getCredential()
    {
        return lastIn != null ? lastIn.getCredential() : savedCredential;
    }
}
