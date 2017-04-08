package zmq.socket.pipeline;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.util.Blob;

public class Pull extends SocketBase
{
    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    public Pull(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PULL;

        fq = new FQ();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all)
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

    @Override
    protected Blob getCredential()
    {
        return fq.getCredential();
    }
}
