package zmq;

public class XSub extends SocketBase
{
    public static class XSubSession extends SessionBase
    {
        public XSubSession(IOThread ioThread, boolean connect,
                SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }
    }

    //  Fair queueing object for inbound pipes.
    private final FQ fq;

    //  Object for distributing the subscriptions upstream.
    private final Dist dist;

    //  The repository of subscriptions.
    private final Trie subscriptions;

    //  If true, 'message' contains a matching message to return on the
    //  next recv call.
    private boolean hashMessage;
    private Msg message;

    //  If true, part of a multipart message was already received, but
    //  there are following parts still waiting.
    private boolean more;
    private static Trie.ITrieHandler sendSubscription;

    static {
        sendSubscription = new Trie.ITrieHandler()
        {
            @Override
            public void added(byte[] data, int size, Object arg)
            {
                Pipe pipe = (Pipe) arg;

                //  Create the subsctription message.
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

            }
        };
    }

    public XSub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_XSUB;
        hashMessage = false;
        more = false;

        options.linger = 0;
        fq = new FQ();
        dist = new Dist();
        subscriptions = new Trie();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean icanhasall)
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
        byte[] data = msg.data();
        // Malformed subscriptions.
        if (data.length < 1 || (data[0] != 0 && data[0] != 1)) {
            throw new IllegalArgumentException("subscription flag");
        }

        // Process the subscription.
        if (data[0] == 1) {
            // this used to filter out duplicate subscriptions,
            // however this is alread done on the XPUB side and
            // doing it here as well breaks ZMQ_XPUB_VERBOSE
            // when there are forwarding devices involved
            //
            subscriptions.add(data , 1);
            return dist.sendToAll(msg);
        }
        else {
            if (subscriptions.rm(data, 1)) {
                return dist.sendToAll(msg);
            }
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
        Msg msg = null;

        //  If there's already a message prepared by a previous call to zmq_poll,
        //  return it straight ahead.
        if (hashMessage) {
            msg = message;
            hashMessage = false;
            more = msg.hasMore();
            return msg;
        }

        //  TODO: This can result in infinite loop in the case of continuous
        //  stream of non-matching messages which breaks the non-blocking recv
        //  semantics.
        while (true) {
            //  Get a message using fair queueing algorithm.
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
        if (hashMessage) {
            return true;
        }

        //  TODO: This can result in infinite loop in the case of continuous
        //  stream of non-matching messages.
        while (true) {
            //  Get a message using fair queueing algorithm.
            message = fq.recv(errno);

            //  If there's no message available, return immediately.
            //  The same when error occurs.
            if (message == null) {
                return false;
            }

            //  Check whether the message matches at least one subscription.
            if (!options.filter || match(message)) {
                hashMessage = true;
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

    private boolean match(Msg msg)
    {
        return subscriptions.check(msg.buf());
    }
}
