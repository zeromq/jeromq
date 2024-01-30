package zmq.io.net;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.poll.IPollEvents;

public abstract class Listener extends Own implements IPollEvents
{
    //  Socket the listener belongs to.
    protected final SocketBase socket;

    protected Listener(IOThread ioThread, SocketBase socket, final Options options)
    {
        super(ioThread, options);
        this.socket = socket;
    }

    public abstract boolean setAddress(String addr);

    public abstract String getAddress();
}
