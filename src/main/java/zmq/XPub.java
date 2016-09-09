package zmq;

import java.util.ArrayDeque;
import java.util.Deque;

class XPub extends SocketBase
{
    public static class XPubSession extends SessionBase
    {
        public XPubSession(IOThread ioThread, boolean connect,
                SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }

    }

    //  List of all subscriptions mapped to corresponding pipes.
    private final Mtrie subscriptions;

    //  Distributor of messages holding the list of outbound pipes.
    private final Dist dist;

    // If true, send all subscription messages upstream, not just
    // unique ones
    private boolean verboseSubs;

    // If true, send all unsubscription messages upstream, not just
    // unique ones
    private boolean verboseUnsubs;

    //  True if we are in the middle of sending a multi-part message.
    private boolean more;

    //  Drop messages if HWM reached, otherwise return with false
    private boolean lossy;

    //  List of pending (un)subscriptions, ie. those that were already
    //  applied to the trie, but not yet received by the user.
    private final Deque<Blob> pendingData;
    private final Deque<Integer> pendingFlags;

    private static Mtrie.IMtrieHandler markAsMatching;
    private static Mtrie.IMtrieHandler sendUnsubscription;

    static {
        markAsMatching = new Mtrie.IMtrieHandler()
        {
            @Override
            public void invoke(Pipe pipe, byte[] data, int size, Object arg)
            {
                XPub self = (XPub) arg;
                self.dist.match(pipe);
            }
        };

        sendUnsubscription = new Mtrie.IMtrieHandler()
        {
            @Override
            public void invoke(Pipe pipe, byte[] data, int size, Object arg)
            {
                XPub self = (XPub) arg;

                if (self.options.type != ZMQ.ZMQ_PUB) {
                    //  Place the unsubscription to the queue of pending (un)sunscriptions
                    //  to be retrived by the user later on.
                    byte[] unsub = new byte[size + 1];
                    unsub[0] = 0;
                    System.arraycopy(data, 0, unsub, 1, size);
                    self.pendingData.add(Blob.createBlob(unsub, false));
                    self.pendingFlags.add(0);
                }
            }
        };
    }

    public XPub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_XPUB;
        verboseSubs = false;
        verboseUnsubs = false;
        more = false;
        lossy = true;

        subscriptions = new Mtrie();
        dist = new Dist();
        pendingData = new ArrayDeque<Blob>();
        pendingFlags = new ArrayDeque<Integer>();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribeToAll)
    {
        assert (pipe != null);
        dist.attach(pipe);

        //  If icanhasall_ is specified, the caller would like to subscribe
        //  to all data on this pipe, implicitly.
        if (subscribeToAll) {
            subscriptions.add(null, pipe);
        }

        //  The pipe is active when attached. Let's read the subscriptions from
        //  it, if any.
        xreadActivated(pipe);
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        //  There are some subscriptions waiting. Let's process them.
        Msg sub = null;
        while ((sub = pipe.read()) != null) {
            //  Apply the subscription to the trie
            byte[] data = sub.data();
            int size = sub.size();
            if (size > 0 && (data[0] == 0 || data[0] == 1)) {
                boolean unique;
                if (data[0] == 0) {
                    unique = subscriptions.rm(data, 1, pipe);
                }
                else {
                    unique = subscriptions.add(data, 1, pipe);
                }

                //  If the subscription is not a duplicate, store it so that it can be
                //  passed to used on next recv call. (Unsubscribe is not verboseSubs.)
                if (options.type == ZMQ.ZMQ_XPUB && (unique || (data[0] == 1 && verboseSubs) ||
                        (data[0] == 0 && verboseUnsubs))) {
                    pendingData.add(Blob.createBlob(data, true));
                    pendingFlags.add(0);
                }
            }
            else {
                //  Process user message coming upstream from xsub socket
                pendingData.add(Blob.createBlob(data, true));
                pendingFlags.add(sub.flags());
            }
        }
    }

    @Override
    protected void xwriteActivated(Pipe pipe)
    {
        dist.activated(pipe);
    }

    @Override
    public boolean xsetsockopt(int option, Object optval)
    {
        if (option == ZMQ.ZMQ_XPUB_VERBOSE) {
            verboseSubs = (Integer) optval == 1;
        }
        else if (option == ZMQ.ZMQ_XPUB_VERBOSE_UNSUBSCRIBE) {
            verboseUnsubs = (Integer) optval == 1;
        }
        else if (option == ZMQ.ZMQ_XPUB_NODROP) {
            lossy = (Integer) optval == 0;
        }
        else {
            return false;
        }

        return true;
    }

    @Override
    protected void xpipeTerminated(Pipe pipe)
    {
        //  Remove the pipe from the trie. If there are topics that nobody
        //  is interested in anymore, send corresponding unsubscriptions
        //  upstream.

        subscriptions.rm(pipe, sendUnsubscription, this, !verboseUnsubs);

        dist.terminated(pipe);
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        boolean msgMore = msg.hasMore();

        //  For the first part of multi-part message, find the matching pipes.
        if (!more) {
            subscriptions.match(msg.buf(), msg.size(),
                    markAsMatching, this);
        }

        if (lossy || dist.checkHwm()) {
            //  Send the message to all the pipes that were marked as matching
            //  in the previous step.
            if (dist.sendToMatching(msg)) {
                //  If we are at the end of multi-part message we can mark all the pipes
                //  as non-matching.
                if (!msgMore) {
                    dist.unmatch();
                }
                more = msgMore;
                return true;
            }
        }
        else {
            errno.set(ZError.EAGAIN);
        }

        return false;
    }

    @Override
    protected boolean xhasOut()
    {
        return dist.hasOut();
    }

    @Override
    protected Msg xrecv()
    {
        //  If there is at least one
        if (pendingData.isEmpty()) {
            errno.set(ZError.EAGAIN);
            return null;
        }

        Blob first = pendingData.pollFirst();
        Msg msg = new Msg(first.data());
        int flags = pendingFlags.pollFirst();
        msg.setFlags(flags);
        return msg;
    }

    @Override
    protected boolean xhasIn()
    {
        return !pendingData.isEmpty();
    }
}
