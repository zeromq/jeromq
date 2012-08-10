package zmq;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class PollItem {

    private SocketBase s;
    private int interest;
    private int ready;
    
    public PollItem(SocketBase s_, int ops) {
        s = s_;
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
        return (ready & SelectionKey.OP_READ) > 0;
    }

    public boolean isWriteable() {
        return (ready & SelectionKey.OP_WRITE) > 0;
    }

    public SelectableChannel getChannel() {
        return s.get_fd();
    }

    public int interestOps() {
        return interest;
    }

    public void readyOps(int readyOps) {
        ready = readyOps;
    }

}
