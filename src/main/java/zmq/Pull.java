package zmq;

class Pull extends SocketBase
{
    public static class PullSession extends SessionBase
    {
        public PullSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    public Pull(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PULL;

        fq = new FQ();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
    {
        assert (pipe != null);
        fq.attach(pipe);
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        fq.activated(pipe);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        fq.terminated(pipe);
    }

    @Override
    public Msg xrecv()
    {
        return fq.recv(errno);
    }

    @Override
    protected boolean xhasIn()
    {
        return fq.hasIn();
    }
}
