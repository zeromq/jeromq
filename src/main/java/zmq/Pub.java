package zmq;

public class Pub extends XPub
{
    public static class PubSession extends XPub.XPubSession
    {
        public PubSession(IOThread ioThread, boolean connect,
                SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    public Pub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);
        options.type = ZMQ.ZMQ_PUB;
    }

    @Override
    protected Msg xrecv()
    {
        //  Messages cannot be received from PUB socket.
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhasIn()
    {
        return false;
    }
}
