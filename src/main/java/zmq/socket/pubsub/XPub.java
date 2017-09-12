package zmq.socket.pubsub;

import java.util.ArrayDeque;
import java.util.Deque;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.pipe.Pipe;
import zmq.socket.pubsub.Mtrie.IMtrieHandler;
import zmq.util.Blob;

public class XPub extends SocketBase
{
    private static final class SendUnsubscription implements IMtrieHandler
    {
        @Override
        public void invoke(Pipe pipe, byte[] data, int size, XPub self)
        {
            self.sendUnsubscription(data, size);
        }
    }

    private static final class MarkAsMatching implements IMtrieHandler
    {
        @Override
        public void invoke(Pipe pipe, byte[] data, int size, XPub self)
        {
            self.markAsMatching(pipe);
        }
    }

    //  List of all subscriptions mapped to corresponding pipes.
    private final Mtrie subscriptions;

    //  Distributor of messages holding the list of outbound pipes.
    private final Dist dist;

    // If true, send all subscription messages upstream, not just
    // unique ones
    private boolean verbose;

    //  True if we are in the middle of sending a multi-part message.
    private boolean more;

    //  Drop messages if HWM reached, otherwise return with false
    private boolean lossy;

    //  List of pending (un)subscriptions, ie. those that were already
    //  applied to the trie, but not yet received by the user.
    private final Deque<Blob>    pendingData;
    private final Deque<Integer> pendingFlags;

    private static final IMtrieHandler markAsMatching     = new MarkAsMatching();
    private static final IMtrieHandler sendUnsubscription = new SendUnsubscription();

    public XPub(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid);

        options.type = ZMQ.ZMQ_XPUB;
        verbose = false;
        more = false;
        lossy = true;

        subscriptions = new Mtrie();
        dist = new Dist();
        pendingData = new ArrayDeque<>();
        pendingFlags = new ArrayDeque<>();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribeToAll)
    {
        assert (pipe != null);
        dist.attach(pipe);

        //  If subscribe_to_all_ is specified, the caller would like to subscribe
        //  to all data on this pipe, implicitly.
        if (subscribeToAll) {
            subscriptions.addOnTop(pipe);
        }

        //  The pipe is active when attached. Let's read the subscriptions from
        //  it, if any.
        xreadActivated(pipe);
    }

    @Override
    protected void xreadActivated(Pipe pipe)
    {
        //  There are some subscriptions waiting. Let's process them.
        Msg sub;
        while ((sub = pipe.read()) != null) {
            //  Apply the subscription to the trie
            int size = sub.size();
            if (size > 0 && (sub.get(0) == 0 || sub.get(0) == 1)) {
                boolean unique;
                if (sub.get(0) == 0) {
                    unique = subscriptions.rm(sub, pipe);
                }
                else {
                    unique = subscriptions.add(sub, pipe);
                }

                //  If the subscription is not a duplicate, store it so that it can be
                //  passed to used on next recv call. (Unsubscribe is not verbose.)
                if (options.type == ZMQ.ZMQ_XPUB && (unique || (sub.get(0) > 0 && verbose))) {
                    pendingData.add(Blob.createBlob(sub));
                    pendingFlags.add(0);
                }
            }
            else {
                //  Process user message coming upstream from xsub socket
                pendingData.add(Blob.createBlob(sub));
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
            verbose = Options.parseBoolean(option, optval);
        }
        else if (option == ZMQ.ZMQ_XPUB_NODROP) {
            lossy = !Options.parseBoolean(option, optval);
        }
        else {
            errno.set(ZError.EINVAL);
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

        subscriptions.rm(pipe, sendUnsubscription, this);

        dist.terminated(pipe);
    }

    private void markAsMatching(Pipe pipe)
    {
        dist.match(pipe);
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        boolean msgMore = msg.hasMore();

        //  For the first part of multi-part message, find the matching pipes.
        if (!more) {
            subscriptions.match(msg.buf(), msg.size(), markAsMatching, this);
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
                return true; //  Yay, sent successfully
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

    private void sendUnsubscription(byte[] data, int size)
    {
        if (options.type != ZMQ.ZMQ_PUB) {
            //  Place the unsubscription to the queue of pending (un)sunscriptions
            //  to be retrieved by the user later on.
            byte[] unsub = new byte[size + 1];
            unsub[0] = 0;
            System.arraycopy(data, 0, unsub, 1, size);
            pendingData.add(Blob.createBlob(unsub));
            pendingFlags.add(0);
        }
    }
}
