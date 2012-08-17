package zmq;

public class Sub extends XSub {

    public static class SubSession extends XSub.XSubSession {

        public SubSession(IOThread io_thread_, boolean connect_,
                SocketBase socket_, Options options_, Address addr_) {
            super(io_thread_, connect_, socket_, options_, addr_);
        }

    }
    
    public Sub(Ctx parent_, int tid_, int sid_) {
        super(parent_, tid_, sid_);
        
        options.type = ZMQ.ZMQ_SUB;

        //  Switch filtering messages on (as opposed to XSUB which where the
        //  filtering is off).
        options.filter = true;
    }

    @Override
    public void xsetsockopt (int option_, Object optval_)
    {
        if (option_ != ZMQ.ZMQ_SUBSCRIBE && option_ != ZMQ.ZMQ_UNSUBSCRIBE) {
            return ;
        }

        String val = (String) optval_;
        //  Create the subscription message.
        Msg msg = new Msg(val.length() + 1);
        if (option_ == ZMQ.ZMQ_SUBSCRIBE)
            msg.put((byte)1);
        else if (option_ == ZMQ.ZMQ_UNSUBSCRIBE)
            msg.put((byte)0);
        msg.put (val.getBytes(),1);

        //  Pass it further on in the stack.
        boolean rc = super.xsend (msg, 0);
        if (!rc)
            throw new IllegalArgumentException();
    }
    
    @Override
    protected boolean xsend (Msg msg_, int flags_)
    {
        //  Overload the XSUB's send.
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhas_out ()
    {
        //  Overload the XSUB's send.
        return false;
    }


}
