package zmq.socket.radiodish;

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
import zmq.socket.pubsub.Dist;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Radio extends SocketBase
{
    private final Map<String, List<Pipe>> subscriptions;
    private final Dist dist;

    public Radio(Ctx parent, int tid, int sid)
    {
        super(parent, tid, sid, true);

        options.type = ZMQ.ZMQ_RADIO;

        subscriptions = new HashMap<>();
        dist = new Dist();
    }

    @Override
    public void xattachPipe(Pipe pipe, boolean subscribe2all, boolean isLocallyInitiated)
    {
        assert (pipe != null);

        pipe.setNoDelay();
        dist.attach(pipe);
        xreadActivated(pipe);
    }

    @Override
    public void xreadActivated(Pipe pipe)
    {
        Msg msg = pipe.read();
        while (msg != null) {
            if (msg.isJoin()) {
                if (! subscriptions.containsKey(msg.getGroup())) {
                    subscriptions.put(msg.getGroup(), new ArrayList<>());
                }
                List<Pipe> pipes = subscriptions.get(msg.getGroup());
                pipes.add(pipe);
            }
            else if (msg.isLeave()) {
                List<Pipe> pipes = subscriptions.get(msg.getGroup());
                if (pipes != null) {
                    pipes.remove(pipe);
                    if (pipes.isEmpty()) {
                        subscriptions.remove(msg.getGroup());
                    }
                }
            }

            msg = pipe.read();
        }
    }

    @Override
    public void xwriteActivated(Pipe pipe)
    {
        dist.activated(pipe);
    }

    @Override
    public void xpipeTerminated(Pipe pipe)
    {
        Iterator<Entry<String, List<Pipe>>> i = subscriptions.entrySet().iterator();
        while (i.hasNext()) {
            Entry<String, List<Pipe>> entry = i.next();
            entry.getValue().remove(pipe);
            if (entry.getValue().isEmpty()) {
                i.remove();
            }
        }

        dist.terminated(pipe);
    }

    @Override
    protected boolean xsend(Msg msg)
    {
        //  SERVER sockets do not allow multipart data (ZMQ_SNDMORE)
        if (msg.hasMore()) {
            errno.set(ZError.EINVAL);
            return false;
        }

        dist.unmatch();

        List<Pipe> range = subscriptions.get(msg.getGroup());
        if (range != null) {
            for (Pipe pipe : range) {
                dist.match(pipe);
            }
        }

        dist.sendToMatching(msg);

        return true;
    }

    @Override
    protected Msg xrecv()
    {
        errno.set(ZError.ENOTSUP);

        //  Messages cannot be received from RADIO socket.
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean xhasOut()
    {
        return dist.hasOut();
    }

    public static class RadioSession extends SessionBase
    {
        enum State
        {
            GROUP,
            BODY
        }

        private State state;
        private Msg pending;

        public RadioSession(IOThread ioThread, boolean connect, SocketBase socket, final Options options,
                            final Address addr)
        {
            super(ioThread, connect, socket, options, addr);

            state = State.GROUP;
        }

        @Override
        public boolean pushMsg(Msg msg)
        {
            if (msg.isCommand()) {
                byte commandNameSize = msg.get(0);

                if (msg.size() < commandNameSize + 1) {
                    return super.pushMsg(msg);
                }

                byte[] data = msg.data();

                String commandName = new String(data, 1, commandNameSize, StandardCharsets.US_ASCII);

                int groupLength;
                String group;
                Msg joinLeaveMsg = new Msg();

                // Set the msg type to either JOIN or LEAVE
                if (commandName.equals("JOIN")) {
                    groupLength = msg.size() - 5;
                    group = new String(data, 5, groupLength, StandardCharsets.US_ASCII);
                    joinLeaveMsg.initJoin();
                }
                else if (commandName.equals("LEAVE")) {
                    groupLength = msg.size() - 6;
                    group = new String(data, 6, groupLength, StandardCharsets.US_ASCII);
                    joinLeaveMsg.initLeave();
                }
                // If it is not a JOIN or LEAVE just push the message
                else {
                    return super.pushMsg(msg);
                }

                //  Set the group
                joinLeaveMsg.setGroup(group);

                //  Push the join or leave command
                msg = joinLeaveMsg;
                return super.pushMsg(msg);
            }

            return super.pushMsg(msg);
        }

        @Override
        protected Msg pullMsg()
        {
            Msg msg;

            switch (state) {
                case GROUP:
                    pending = super.pullMsg();
                    if (pending == null) {
                        return null;
                    }

                    //  First frame is the group
                    msg = new Msg(pending.getGroup().getBytes(StandardCharsets.US_ASCII));
                    msg.setFlags(Msg.MORE);

                    //  Next status is the body
                    state = State.BODY;
                    break;
                case BODY:
                    msg = pending;
                    state = State.GROUP;
                    break;
                default:
                    throw new IllegalStateException();
            }

            return msg;
        }

        @Override
        protected void reset()
        {
            super.reset();
            state = State.GROUP;
        }
    }
}
