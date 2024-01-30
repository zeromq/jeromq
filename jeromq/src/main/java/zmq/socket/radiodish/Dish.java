package zmq.socket.radiodish;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import zmq.Ctx;
import zmq.Msg;
import zmq.Options;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.pipe.Pipe;
import zmq.socket.FQ;
import zmq.socket.pubsub.Dist;

public class Dish extends SocketBase
{
    // Fair queueing object for inbound pipes.
    private final FQ fq;

    // Object for distributing the subscriptions upstream.
    private final Dist dist;

    // The repository of subscriptions.
    private final Set<String> subscriptions;

    // If true, 'message' contains a matching message to return on the
    // next recv call.
    private Msg pendingMsg;

    //  Holds the prefetched message.
    public Dish(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);

        options.type = ZMQ.ZMQ_DISH;

        // When socket is being closed down we don't want to wait till pending
        // subscription commands are sent to the wire.
        options.linger = 0;

        fq = new FQ();
        dist = new Dist();
        subscriptions = new HashSet<>();
    }

    @Override
    protected void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        fq.attach(pipe);
        dist.attach(pipe);

        // Send all the cached subscriptions to the new upstream peer.
        sendSubscriptions(pipe);
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
        // Send all the cached subscriptions to the hiccuped pipe.
        sendSubscriptions(pipe);
    }

    @Override
    protected boolean xjoin(String group)
    {
        if (group.length() > Msg.MAX_GROUP_LENGTH) {
            errno.set(ZError.EINVAL);
            return false;
        }

        // User cannot join same group twice
        if (!subscriptions.add(group)) {
            errno.set(ZError.EINVAL);
            return false;
        }

        Msg msg = new Msg();
        msg.initJoin();
        msg.setGroup(group);

        dist.sendToAll(msg);

        return true;
    }

    @Override
    protected boolean xleave(String group)
    {
        if (group.length() > Msg.MAX_GROUP_LENGTH) {
            errno.set(ZError.EINVAL);
            return false;
        }

        if (!subscriptions.remove(group)) {
            errno.set(ZError.EINVAL);
            return false;
        }

        Msg msg = new Msg();
        msg.initLeave();
        msg.setGroup(group);

        dist.sendToAll(msg);

        return true;
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        errno.set(ZError.ENOTSUP);

        throw new UnsupportedOperationException();
    }

    @Override
    protected Msg xrecv()
    {
        // If there's already a message prepared by a previous call to poll,
        // return it straight ahead.
        if (pendingMsg != null) {
            Msg msg = pendingMsg;
            pendingMsg = null;
            return msg;
        }

        return xxrecv();
    }

    private Msg xxrecv()
    {
        // Get a message using fair queueing algorithm.
        Msg msg = fq.recv(errno);

        // If there's no message available, return immediately.
        // The same when error occurs.
        if (msg == null) {
            return null;
        }

        // Skip non matching messages
        while (!subscriptions.contains(msg.getGroup())) {
            msg = fq.recv(errno);
            if (msg == null) {
                return null;
            }
        }

        //  Found a matching message
        return msg;
    }

    @Override
    protected boolean xhasIn()
    {
        // If there's already a message prepared by a previous call to zmq_poll,
        // return straight ahead.
        if (pendingMsg != null) {
            return true;
        }

        Msg msg = xxrecv();
        if (msg == null) {
            return false;
        }

        //  Matching message found
        pendingMsg = msg;
        return true;
    }

    @Override
    protected boolean xhasOut()
    {
        // Subscription can be added/removed anytime.
        return true;
    }

    private void sendSubscriptions(Pipe pipe)
    {
        for (String s : subscriptions) {
            Msg msg = new Msg();
            msg.initJoin();
            msg.setGroup(s);
            pipe.write(msg);
        }

        pipe.flush();
    }

    public static class DishSession extends SessionBase
    {
        static final byte[] JOIN_BYTES = "\4JOIN".getBytes(StandardCharsets.US_ASCII);
        static final byte[] LEAVE_BYTES = "\5LEAVE".getBytes(StandardCharsets.US_ASCII);

        enum State
        {
            GROUP,
            BODY
        }

        private State state;
        private String group;

        public DishSession(IOThread ioThread, boolean connect, SocketBase socket, final Options options,
                           final Address addr)
        {
            super(ioThread, connect, socket, options, addr);

            state = State.GROUP;
            group = "";
        }

        @Override
        public boolean pushMsg(Msg msg)
        {
            switch (state) {
            case GROUP:
                if (!msg.hasMore()) {
                    errno.set(ZError.EFAULT);
                    return false;
                }

                if (msg.size() > Msg.MAX_GROUP_LENGTH) {
                    errno.set(ZError.EFAULT);
                    return false;
                }

                group = new String(msg.data(), StandardCharsets.US_ASCII);
                state = State.BODY;

                return true;
            case BODY:
                //  Set the message group
                msg.setGroup(group);

                //  Thread safe socket doesn't support multipart messages
                if (msg.hasMore()) {
                    errno.set(ZError.EFAULT);
                    return false;
                }

                //  Push message to dish socket
                boolean rc = super.pushMsg(msg);
                if (rc) {
                    state = State.GROUP;
                }

                return rc;
            default:
                throw new IllegalStateException();
            }
        }

        @Override
        protected Msg pullMsg()
        {
            Msg msg = super.pullMsg();
            if (msg == null) {
                return null;
            }

            if (!msg.isJoin() && !msg.isLeave()) {
                return msg;
            }

            Msg command;

            byte[] groupBytes = msg.getGroup().getBytes(StandardCharsets.US_ASCII);

            if (msg.isJoin()) {
                command = new Msg(groupBytes.length + 5);
                command.put(JOIN_BYTES);
            }
            else {
                command = new Msg(groupBytes.length + 6);
                command.put(LEAVE_BYTES);
            }

            command.setFlags(Msg.COMMAND);

            //  Copy the group
            command.put(groupBytes);

            return command;
        }

        @Override
        protected void reset()
        {
            super.reset();
            state = State.GROUP;
        }
    }
}
