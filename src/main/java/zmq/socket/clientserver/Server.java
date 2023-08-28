package zmq.socket.clientserver;

import zmq.SocketBase;
import zmq.Msg;
import zmq.Ctx;
import zmq.ZMQ;
import zmq.ZError;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.util.Blob;
import zmq.util.Utils;
import zmq.util.ValueReference;

import java.util.HashMap;
import java.util.Map;

//TODO: This class uses O(n) scheduling. Rewrite it to use O(1) algorithm.
public class Server extends SocketBase
{
    //  Fair queuing object for inbound pipes.
    private final FQ fq;

    static class Outpipe
    {
        private final Pipe pipe;
        private boolean active;

        public Outpipe(Pipe pipe, boolean active)
        {
            this.pipe = pipe;
            this.active = active;
        }
    }

    //  Outbound pipes indexed by the peer IDs.
    private final Map<Integer, Outpipe> outpipes;

    //  Routing IDs are generated. It's a simple increment and wrap-over
    //  algorithm. This value is the next ID to use (if not used already).
    private int nextRid;

    public Server(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);
        nextRid = Utils.randomInt();

        options.type = ZMQ.ZMQ_SERVER;
        options.canSendHelloMsg = true;
        options.canReceiveDisconnectMsg = true;

        fq = new FQ();
        outpipes = new HashMap<>();
    }

    @Override
    protected void destroy()
    {
        assert (outpipes.isEmpty());

        super.destroy();
    }

    @Override
    public void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        int routingId = nextRid++;
        if (routingId == 0) {
            routingId = nextRid++; //  Never use Routing ID zero
        }

        pipe.setRoutingId(routingId);
        //  Add the record into output pipes lookup table
        Outpipe outpipe = new Outpipe(pipe, true);
        Outpipe prev = outpipes.put(routingId, outpipe);
        assert (prev == null);

        fq.attach(pipe);
    }

    @Override
    public void xpipeTerminated(Pipe pipe)
    {
        Outpipe old = outpipes.remove(pipe.getRoutingId());
        assert (old != null);

        fq.terminated(pipe);
    }

    @Override
    public void xreadActivated(Pipe pipe)
    {
        fq.activated(pipe);
    }

    @Override
    public void xwriteActivated(Pipe pipe)
    {
        Outpipe out = outpipes.get(pipe.getRoutingId());
        assert (out != null);
        assert (!out.active);
        out.active = true;
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  SERVER sockets do not allow multipart data (ZMQ_SNDMORE)
        if (msg.hasMore()) {
            errno.set(ZError.EINVAL);
            return false;
        }

        //  Find the pipe associated with the routing stored in the message.
        int routingId = msg.getRoutingId();
        Outpipe out = outpipes.get(routingId);

        if (out != null) {
            if (!out.pipe.checkWrite()) {
                out.active = false;
                errno.set(ZError.EAGAIN);
                return false;
            }
        }
        else {
            errno.set(ZError.EHOSTUNREACH);
            return false;
        }

        //  Message might be delivered over inproc, so we reset routing id
        msg.resetRoutingId();

        boolean ok = out.pipe.write(msg);
        if (ok) {
            out.pipe.flush();
        }

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        Msg msg;
        ValueReference<Pipe> pipe = new ValueReference<>();
        msg = fq.recvPipe(errno, pipe);

        // Drop any messages with more flag
        while (msg != null && msg.hasMore()) {
            // drop all frames of the current multi-frame message
            msg = fq.recvPipe(errno, null);

            while (msg != null && msg.hasMore()) {
                msg = fq.recvPipe(errno, null);
            }

            // get the new message
            if (msg != null) {
                msg = fq.recvPipe(errno, pipe);
            }
        }

        if (msg == null) {
            return msg;
        }

        assert (pipe.get() != null);

        int routingId = pipe.get().getRoutingId();
        msg.setRoutingId(routingId);

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
        //  In theory, ROUTER socket is always ready for writing. Whether actual
        //  attempt to write succeeds depends on whitch pipe the message is going
        //  to be routed to.
        return true;
    }

    @Override
    protected Blob getCredential()
    {
        return fq.getCredential();
    }

    @Override
    protected boolean xdisconnectPeer(int routingId)
    {
        Outpipe out = outpipes.get(routingId);
        if (out != null) {
            out.pipe.terminate(false);
            return true;
        }

        errno.set(ZError.EHOSTUNREACH);
        return false;
    }
}
