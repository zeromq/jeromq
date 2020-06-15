package zmq.socket.scattergather;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.util.Blob;

public class Gather extends SocketBase
{
    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    //  Holds the prefetched message.
    public Gather(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);

        options.type = ZMQ.ZMQ_GATHER;

        fq = new FQ();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        fq.attach(pipe);
    }

    @Override
    protected Msg xrecv()
    {
        Msg msg = fq.recvPipe(errno, null);

        // Drop any messages with more flag
        while (msg != null && msg.hasMore()) {
            // drop all frames of the current multi-frame message
            msg = fq.recvPipe(errno, null);

            while (msg != null && msg.hasMore()) {
                fq.recvPipe(errno, null);
            }

            // get the new message
            if (msg != null) {
                fq.recvPipe(errno, null);
            }
        }

        return msg;
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
}
