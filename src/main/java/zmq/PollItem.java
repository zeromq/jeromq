package zmq;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class PollItem
{
    private SocketBase socket;
    private SelectableChannel channel;
    private int zinterest;
    private int interest;
    private int ready;

    public PollItem(SocketBase socket)
    {
        this.socket = socket;
        channel = null;
        zinterest = -1;
        interest = -1;
    }

    public PollItem(SocketBase socket, int ops)
    {
        this.socket = socket;
        channel = null;
        init(ops);
    }

    public PollItem(SelectableChannel channel, int ops)
    {
        socket = null;
        this.channel = channel;
        init(ops);
    }

    private void init(int ops)
    {
        zinterest = ops;
        int interest = 0;
        if ((ops & ZMQ.ZMQ_POLLIN) > 0) {
            interest |= SelectionKey.OP_READ;
        }
        if ((ops & ZMQ.ZMQ_POLLOUT) > 0) {
            if (socket != null) { // ZMQ Socket get readiness from the mailbox
                interest |= SelectionKey.OP_READ;
            }
            else {
                interest |= SelectionKey.OP_WRITE;
            }
        }
        this.interest = interest;
        ready = 0;
    }

    public final boolean isReadable()
    {
        return (ready & ZMQ.ZMQ_POLLIN) > 0;
    }

    public final boolean isWritable()
    {
        return (ready & ZMQ.ZMQ_POLLOUT) > 0;
    }

    public final boolean isError()
    {
        return (ready & ZMQ.ZMQ_POLLERR) > 0;
    }

    public final SocketBase getSocket()
    {
        return socket;
    }

    public final SelectableChannel getRawSocket()
    {
        return channel;
    }

    protected final SelectableChannel getChannel()
    {
        if (socket != null) {
            return socket.getFD();
        }
        else {
            return channel;
        }
    }

    public final int interestOps()
    {
        return interest;
    }

    public final int zinterestOps()
    {
        return zinterest;
    }

    public final int interestOps(int ops)
    {
        init(ops);
        return interest;
    }

    public final int readyOps(SelectionKey key, int nevents)
    {
        ready = 0;

        if (socket != null) {
            int events = socket.getSocketOpt(ZMQ.ZMQ_EVENTS);
            if (events < 0) {
                return -1;
            }

            if ((zinterest & ZMQ.ZMQ_POLLOUT) > 0 && (events & ZMQ.ZMQ_POLLOUT) > 0) {
                ready |= ZMQ.ZMQ_POLLOUT;
            }
            if ((zinterest & ZMQ.ZMQ_POLLIN) > 0 && (events & ZMQ.ZMQ_POLLIN) > 0) {
                ready |= ZMQ.ZMQ_POLLIN;
            }
        }
        else if (nevents > 0) {
            if (key.isReadable()) {
                ready |= ZMQ.ZMQ_POLLIN;
            }
            if (key.isWritable()) {
                ready |= ZMQ.ZMQ_POLLOUT;
            }
            if (!key.isValid() || key.isAcceptable() || key.isConnectable()) {
                ready |= ZMQ.ZMQ_POLLERR;
            }
        }

        return ready;
    }

    public final int readyOps()
    {
        return ready;
    }
}
