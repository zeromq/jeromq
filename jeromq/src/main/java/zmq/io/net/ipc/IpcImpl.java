package zmq.io.net.ipc;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.ServiceLoader;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.Listener;
import zmq.io.net.ipc.loopback.TcpLoopbackIpcImpl;

public interface IpcImpl
{
    static IpcImpl get()
    {
        ServiceLoader<IpcImpl> loader = ServiceLoader.load(IpcImpl.class);
        Iterator<IpcImpl> implementations = loader.iterator();
        if (implementations.hasNext()) {
            return implementations.next();
        }

        return new TcpLoopbackIpcImpl();
    }

    boolean isImplementedViaTcpLoopback();

    Address.IZAddress createAddress(String addr);

    boolean isIpcAddress(SocketAddress address);

    String getAddress(SocketAddress address);

    Own createConnector(IOThread ioThread, SessionBase session, Options options, Address addr, boolean wait);

    Listener createListener(IOThread ioThread, SocketBase socket, Options options);
}
