package zmq.socket.pubsub;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;

public class Sub extends XSub
{
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
            errno.set(ZError.EINVAL);
            return false;
        }

        if (optval == null) {
            errno.set(ZError.EINVAL);
            return false;
        }
        byte[] val = Options.parseBytes(option, optval);

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
            errno.set(ZError.EINVAL);
            throw new IllegalStateException("Failed to send subscribe/unsubscribe message");
        }

        return true;
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        errno.set(ZError.ENOTSUP);
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
