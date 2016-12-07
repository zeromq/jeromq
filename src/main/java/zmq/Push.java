package zmq;

public class Push extends SocketBase
{
    public static class PushSession extends SessionBase
    {
        public PushSession(IOThread ioThread, boolean connect,
            SocketBase socket, final Options options,
            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    //  Load balancer managing the outbound pipes.
    private final LB lb;

    public Push(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PUSH;

        lb = new LB();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
    {
        assert (pipe != null);
        lb.attach(pipe);
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        lb.activated(pipe);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        lb.terminated(pipe);
    }

    @Override
    public boolean xsend(Msg msg)
    {
        return lb.send(msg, errno);
    }

    @Override
    protected boolean xhasOut()
    {
        return lb.hasOut();
    }
}
