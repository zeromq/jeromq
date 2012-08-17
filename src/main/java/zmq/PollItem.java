package zmq;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class PollItem {

    private SocketBase s;
    private SelectableChannel c;
    private int zinterest;
    private int interest;
    private int ready;
    
    public PollItem(SocketBase s_, int ops) {
        s = s_;
        c = null;
        init (ops);
    }
    
    public PollItem(SelectableChannel c_, int ops) {
        s = null;
        c = c_;
    }
    
    private void init (int ops) {
        zinterest = ops;
        int interest_ = 0;
        if ((ops & ZMQ.ZMQ_POLLIN) > 0) {
            interest_ |= SelectionKey.OP_READ;
        }
        if ((ops & ZMQ.ZMQ_POLLOUT) > 0) {
            interest_ |= SelectionKey.OP_WRITE;
        }
        interest = interest_;
        ready = 0;
    }

    public boolean isReadable() {
        return (ready & ZMQ.ZMQ_POLLIN) > 0;
    }

    public boolean isWriteable() {
        return (ready & ZMQ.ZMQ_POLLOUT) > 0;
    }

    public SelectableChannel getChannel() {
        if (s != null)
            return s.get_fd();
        else 
            return c;
    }

    public int interestOps() {
        return interest;
    }
    
    public boolean readyOps(SelectionKey key) {
        ready = 0;
        
        if (s != null) {
            int events = s.getsockopt(ZMQ.ZMQ_EVENTS);
            if ( (zinterest & ZMQ.ZMQ_POLLOUT) > 0 && (events & ZMQ.ZMQ_POLLOUT) > 0 ) {
                ready |= ZMQ.ZMQ_POLLOUT;
            }
            if ( (zinterest & ZMQ.ZMQ_POLLIN) > 0 && (events & ZMQ.ZMQ_POLLIN) > 0 ) {
                ready |= ZMQ.ZMQ_POLLIN;
            }
        } else {
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
        
        return ready > 0;
    }

}
