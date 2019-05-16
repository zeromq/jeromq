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

    //  List of manual subscriptions mapped to corresponding pipes.
    private final Mtrie manualSubscriptions;

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

    //  Subscriptions will not bed added automatically, only after calling set option with ZMQ_SUBSCRIBE or ZMQ_UNSUBSCRIBE
    private boolean manual;

    //  Last pipe that sent subscription message, only used if xpub is on manual
    private Pipe lastPipe;

    // Pipes that sent subscriptions messages that have not yet been processed, only used if xpub is on manual
    private final Deque<Pipe> pendingPipes;

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
        verboseSubs = false;
        verboseUnsubs = false;
        more = false;
        lossy = true;
        manual = false;

        subscriptions = new Mtrie();
        manualSubscriptions = new Mtrie();
        dist = new Dist();
        lastPipe = null;
        pendingPipes = new ArrayDeque<>();
        pendingData = new ArrayDeque<>();
        pendingFlags = new ArrayDeque<>();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribeToAll, boolean isLocallyInitiated)
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
            boolean subscribe = false;
            int size = sub.size();
            if (size > 0 && (sub.get(0) == 0 || sub.get(0) == 1)) {
                subscribe = sub.get(0) == 1;
            }
            else {
                //  Process user message coming upstream from xsub socket
                pendingData.add(Blob.createBlob(sub));
                pendingFlags.add(sub.flags());
                continue;
            }

            if (manual) {
                // Store manual subscription to use on termination
                if (!subscribe) {
                    manualSubscriptions.rm(sub, pipe);
                }
                else {
                    manualSubscriptions.add(sub, pipe);
                }

                pendingPipes.add(pipe);

                //  ZMTP 3.1 hack: we need to support sub/cancel commands, but
                //  we can't give them back to userspace as it would be an API
                //  breakage since the payload of the message is completely
                //  different. Manually craft an old-style message instead.
                pendingData.add(Blob.createBlob(sub));
                pendingFlags.add(0);
            }
            else {
                boolean notify;
                if (!subscribe) {
                    notify = subscriptions.rm(sub, pipe) || verboseUnsubs;
                }
                else {
                    notify = subscriptions.add(sub, pipe) || verboseSubs;
                }

                //  If the request was a new subscription, or the subscription
                //  was removed, or verbose mode is enabled, store it so that
                //  it can be passed to the user on next recv call.
                if (options.type == ZMQ.ZMQ_XPUB && notify) {
                    pendingData.add(Blob.createBlob(sub));
                    pendingFlags.add(0);
                }
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
        if (option == ZMQ.ZMQ_XPUB_VERBOSE || option == ZMQ.ZMQ_XPUB_VERBOSER
                || option == ZMQ.ZMQ_XPUB_NODROP || option == ZMQ.ZMQ_XPUB_MANUAL) {
            if (option == ZMQ.ZMQ_XPUB_VERBOSE) {
                verboseSubs = Options.parseBoolean(option, optval);
                verboseUnsubs = false;
            }
            else if (option == ZMQ.ZMQ_XPUB_VERBOSER) {
                verboseSubs = Options.parseBoolean(option, optval);
                verboseUnsubs = verboseSubs;
            }
            else if (option == ZMQ.ZMQ_XPUB_NODROP) {
                lossy = !Options.parseBoolean(option, optval);
            }
            else if (option == ZMQ.ZMQ_XPUB_MANUAL) {
                manual = Options.parseBoolean(option, optval);
            }
        }
        else if (option == ZMQ.ZMQ_SUBSCRIBE && manual) {
            if (null != lastPipe) {
                String val = Options.parseString(option, optval);
                subscriptions.add(new Msg(val.getBytes()), lastPipe);
            }
        }
        else if (option == ZMQ.ZMQ_UNSUBSCRIBE && manual) {
            if (null != lastPipe) {
                String val = Options.parseString(option, optval);
                subscriptions.rm(new Msg(val.getBytes()), lastPipe);
            }
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
        if (manual) {
            manualSubscriptions.rm(pipe, sendUnsubscription, this);

            subscriptions.rm(pipe, (p, d, s, self)-> { }, this);
        }
        else {
            //  Remove the pipe from the trie. If there are topics that nobody
            //  is interested in anymore, send corresponding unsubscriptions
            //  upstream.

            subscriptions.rm(pipe, sendUnsubscription, this);
        }

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

        // User is reading a message, set lastPipe and remove it from the deque
        if (manual && !pendingPipes.isEmpty()) {
            lastPipe = pendingPipes.pollFirst();
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

            if (manual) {
                lastPipe = null;
                pendingPipes.clear();
            }
        }
    }
}
