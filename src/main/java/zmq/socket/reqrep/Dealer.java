package zmq.socket.reqrep;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.socket.LB;
import zmq.util.Blob;
import zmq.util.ValueReference;

public class Dealer extends SocketBase
{
    //  Messages are fair-queued from inbound pipes. And load-balanced to
    //  the outbound pipes.
    private final FQ fq;
    private final LB lb;

    // if true, send an empty message to every connected router peer
    private boolean probeRouter;

    //  Holds the prefetched message.
    public Dealer(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_DEALER;

        fq = new FQ();
        lb = new LB();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all)
    {
        assert (pipe != null);

        if (probeRouter) {
            Msg probe = new Msg();
            pipe.write(probe);
            // assert (rc == 0) is not applicable here, since it is not a bug.
            pipe.flush();
        }
        fq.attach(pipe);
        lb.attach(pipe);
    }

    @Override
    protected boolean xsetsockopt(int option, Object optval)
    {
        if (option == ZMQ.ZMQ_PROBE_ROUTER) {
            probeRouter = Options.parseBoolean(option, optval);
            return true;
        }
        errno.set(ZError.EINVAL);
        return false;
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        return sendpipe(msg, null);
    }

    @Override
    protected Msg xrecv()
    {
        return recvpipe(null);
    }

    @Override
    protected boolean xhasIn()
    {
        return fq.hasIn();
    }

    @Override
    protected boolean xhasOut()
    {
        return lb.hasOut();
    }

    @Override
    protected Blob getCredential()
    {
        return fq.getCredential();
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        fq.activated(pipe);
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        lb.activated(pipe);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        fq.terminated(pipe);
        lb.terminated(pipe);
    }

    protected final boolean sendpipe(Msg msg, ValueReference<Pipe> pipe)
    {
        return lb.sendpipe(msg, errno, pipe);
    }

    protected final Msg recvpipe(ValueReference<Pipe> pipe)
    {
        return fq.recvPipe(errno, pipe);
    }
}
