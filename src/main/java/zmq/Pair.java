package zmq;

public class Pair extends SocketBase
{
    public static class PairSession extends SessionBase
    {
        public PairSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    private Pipe pipe;

    public Pair(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PAIR;
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
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
        if (pipe == null || !pipe.write(msg)) {
            errno.set(ZError.EAGAIN);
            return false;
        }

        if ((msg.flags() & ZMQ.ZMQ_SNDMORE) == 0) {
            pipe.flush();
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        //  Deallocate old content of the message.
        Msg msg = pipe == null ? null : pipe.read();
        if (msg == null) {
            //  Initialise the output parameter to be a 0-byte message.
            errno.set(ZError.EAGAIN);
            return null;
        }
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
}
