package zmq;

public class Sub extends XSub
{
    public static class SubSession extends XSub.XSubSession
    {
        public SubSession(IOThread ioThread, boolean connect,
                SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }

    }

    public Sub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_SUB;

        //  Switch filtering messages on (as opposed to XSUB which where the
        //  filtering is off).
        options.filter = true;
    }

    @Override
    public boolean xsetsockopt(int option, Object optval)
    {
        if (option != ZMQ.ZMQ_SUBSCRIBE && option != ZMQ.ZMQ_UNSUBSCRIBE) {
            return false;
        }

        byte[] val;

        if (optval instanceof String) {
            val = ((String) optval).getBytes(ZMQ.CHARSET);
        }
        else if (optval instanceof byte[]) {
            val = (byte[]) optval;
        }
        else {
            throw new IllegalArgumentException();
        }

        //  Create the subscription message.
        Msg msg = new Msg(val.length + 1);
        if (option == ZMQ.ZMQ_SUBSCRIBE) {
            msg.put((byte) 1);
        }
        else {
            // option = ZMQ.ZMQ_UNSUBSCRIBE
            msg.put((byte) 0);
        }
        msg.put(val);
        //  Pass it further on in the stack.
        boolean rc = super.xsend(msg);
        if (!rc) {
            throw new IllegalStateException("Failed to send subscribe/unsubscribe message");
        }

        return true;
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  Overload the XSUB's send.
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhasOut()
    {
        //  Overload the XSUB's send.
        return false;
    }
}
