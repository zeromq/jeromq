package zmq.socket.clientserver;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.socket.LB;
import zmq.util.Blob;

public class Client extends SocketBase
{
    //  Messages are fair-queued from inbound pipes. And load-balanced to
    //  the outbound pipes.
    private final FQ fq;
    private final LB lb;

    //  Holds the prefetched message.
    public Client(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);

        options.type = ZMQ.ZMQ_CLIENT;
        options.canSendHelloMsg = true;
        options.canReceiveHiccupMsg = true;

        fq = new FQ();
        lb = new LB();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        fq.attach(pipe);
        lb.attach(pipe);
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  CLIENT sockets do not allow multipart data (ZMQ_SNDMORE)
        if (msg.hasMore()) {
            errno.set(ZError.EINVAL);
            return false;
        }
        return lb.sendpipe(msg, errno, null);
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
}
