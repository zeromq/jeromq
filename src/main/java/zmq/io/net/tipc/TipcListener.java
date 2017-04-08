package zmq.io.net.tipc;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.ipc.IpcAddress;
import zmq.io.net.tcp.TcpListener;

public class TipcListener extends TcpListener
{
    private IpcAddress address;

    public TipcListener(IOThread ioThread, SocketBase socket, final Options options)
    {
        super(ioThread, socket, options);
        // TODO V4 implement tipc
        throw new UnsupportedOperationException("TODO implement tipc");
    }
}
