package zmq.io.net.ipc;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.tcp.TcpConnecter;

public class IpcConnecter extends TcpConnecter
{
    public IpcConnecter(IOThread ioThread, SessionBase session, final Options options, final Address addr, boolean wait)
    {
        super(ioThread, session, options, addr, wait);
    }
}
