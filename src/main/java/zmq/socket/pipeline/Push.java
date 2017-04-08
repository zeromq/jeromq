package zmq.socket.pipeline;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.LB;

public class Push extends SocketBase
{
    //  Load balancer managing the outbound pipes.
    private final LB lb;

    public Push(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PUSH;

        lb = new LB();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all)
    {
        assert (pipe != null);

        //  Don't delay pipe termination as there is no one
        //  to receive the delimiter.
        pipe.setNoDelay();

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
        return lb.sendpipe(msg, errno, null);
    }

    @Override
    protected boolean xhasOut()
    {
        return lb.hasOut();
    }
}
