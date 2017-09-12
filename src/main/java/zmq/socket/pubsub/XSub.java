package zmq.socket.pubsub;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.socket.pubsub.Trie.ITrieHandler;
import zmq.util.Blob;

public class XSub extends SocketBase
{
    private final class SendSubscription implements ITrieHandler
    {
        @Override
        public void added(byte[] data, int size, Pipe pipe)
        {
            sendSubscription(data, size, pipe);
        }
    }

    //  Fair queuing object for inbound pipes.
    private final FQ fq;

    //  Object for distributing the subscriptions upstream.
    private final Dist dist;

    //  The repository of subscriptions.
    private final Trie subscriptions;

    //  If true, 'message' contains a matching message to return on the
    //  next recv call.
    private boolean hasMessage;
    private Msg     message;

    //  If true, part of a multipart message was already received, but
    //  there are following parts still waiting.
    private boolean more;

    private final ITrieHandler sendSubscription = new SendSubscription();

    public XSub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_XSUB;
        hasMessage = false;
        more = false;

        //  When socket is being closed down we don't want to wait till pending
        //  subscription commands are sent to the wire.
        options.linger = 0;

        fq = new FQ();
        dist = new Dist();
        subscriptions = new Trie();

        message = new Msg();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all)
    {
        assert (pipe != null);
        fq.attach(pipe);
        dist.attach(pipe);

        //  Send all the cached subscriptions to the new upstream peer.
        subscriptions.apply(sendSubscription, pipe);
        pipe.flush();
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        fq.activated(pipe);
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        dist.activated(pipe);
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        fq.terminated(pipe);
        dist.terminated(pipe);
    }

    @Override
    protected void xhiccuped(Pipe pipe)
    {
        //  Send all the cached subscriptions to the hiccuped pipe.
        subscriptions.apply(sendSubscription, pipe);
        pipe.flush();
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        final int size = msg.size();

        if (size > 0 && msg.get(0) == 1) {
            //  Process subscribe message
            //  This used to filter out duplicate subscriptions,
            //  however this is already done on the XPUB side and
            //  doing it here as well breaks ZMQ_XPUB_VERBOSE
            //  when there are forwarding devices involved.
            subscriptions.add(msg, 1, size - 1);
            return dist.sendToAll(msg);
        }
        else if (size > 0 && msg.get(0) == 0) {
            //  Process unsubscribe message
            if (subscriptions.rm(msg, 1, size - 1)) {
                return dist.sendToAll(msg);
            }
        }
        else {
            //  User message sent upstream to XPUB socket
            return dist.sendToAll(msg);
        }
        return true;
    }

    @Override
    protected boolean xhasOut()
    {
        //  Subscription can be added/removed anytime.
        return true;
    }

    @Override
    protected Msg xrecv()
    {
        Msg msg;

        //  If there's already a message prepared by a previous call to zmq_poll,
        //  return it straight ahead.
        if (hasMessage) {
            msg = message;
            hasMessage = false;
            more = msg.hasMore();
            return msg;
        }

        //  TODO: This can result in infinite loop in the case of continuous
        //  stream of non-matching messages which breaks the non-blocking recv
        //  semantics.
        while (true) {
            //  Get a message using fair queuing algorithm.
            msg = fq.recv(errno);

            //  If there's no message available, return immediately.
            //  The same when error occurs.
            if (msg == null) {
                return null;
            }

            //  Check whether the message matches at least one subscription.
            //  Non-initial parts of the message are passed
            if (more || !options.filter || match(msg)) {
                more = msg.hasMore();
                return msg;
            }

            //  Message doesn't match. Pop any remaining parts of the message
            //  from the pipe.
            while (msg.hasMore()) {
                msg = fq.recv(errno);
                assert (msg != null);
            }
        }
    }

    @Override
    protected boolean xhasIn()
    {
        //  There are subsequent parts of the partly-read message available.
        if (more) {
            return true;
        }

        //  If there's already a message prepared by a previous call to zmq_poll,
        //  return straight ahead.
        if (hasMessage) {
            return true;
        }

        //  TODO: This can result in infinite loop in the case of continuous
        //  stream of non-matching messages.
        while (true) {
            //  Get a message using fair queuing algorithm.
            message = fq.recv(errno);

            //  If there's no message available, return immediately.
            //  The same when error occurs.
            if (message == null) {
                assert (errno.is(ZError.EAGAIN));
                return false;
            }

            //  Check whether the message matches at least one subscription.
            if (!options.filter || match(message)) {
                hasMessage = true;
                return true;
            }

            //  Message doesn't match. Pop any remaining parts of the message
            //  from the pipe.
            while (message.hasMore()) {
                message = fq.recv(errno);
                assert (message != null);
            }
        }
    }

    @Override
    protected Blob getCredential()
    {
        return fq.getCredential();
    }

    private boolean match(Msg msg)
    {
        return subscriptions.check(msg.buf());
    }

    private boolean sendSubscription(byte[] data, int size, Pipe pipe)
    {
        //  Create the subscription message.
        Msg msg = new Msg(size + 1);
        msg.put((byte) 1).put(data, 0, size);

        //  Send it to the pipe.
        boolean sent = pipe.write(msg);
        //  If we reached the SNDHWM, and thus cannot send the subscription, drop
        //  the subscription message instead. This matches the behaviour of
        //  setSocketOption(ZMQ_SUBSCRIBE, ...), which also drops subscriptions
        //  when the SNDHWM is reached.
        //  if (!sent)
        //    msg.close ();

        return sent;
    }
}
