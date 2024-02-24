package zmq.socket;

import zmq.Ctx;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.clientserver.Server;

public class Peer extends Server
{
    public Peer(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_PEER;
        options.canSendHelloMsg = true;
        options.canReceiveDisconnectMsg = true;
        options.canReceiveHiccupMsg = true;
    }

    @Override
    public void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        super.xattachPipe(pipe, subscribe2all, isLocallyInitiated);
        options.peerLastRoutingId = pipe.getRoutingId();
    }
}
