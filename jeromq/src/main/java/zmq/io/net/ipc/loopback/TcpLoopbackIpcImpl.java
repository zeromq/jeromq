package zmq.io.net.ipc.loopback;

import java.net.SocketAddress;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.Listener;
import zmq.io.net.ipc.IpcImpl;

public class TcpLoopbackIpcImpl implements IpcImpl
{
    @Override
    public boolean isImplementedViaTcpLoopback()
    {
        return true;
    }

    @Override
    public Address.IZAddress createAddress(String addr)
    {
        return new IpcAddress(addr);
    }

    @Override
    public boolean isIpcAddress(SocketAddress address)
    {
        return false;
    }

    @Override
    public String getAddress(SocketAddress address)
    {
        throw new IllegalArgumentException("Address is not unix domain socket address.");
    }

    @Override
    public Own createConnector(IOThread ioThread, SessionBase session, Options options, Address addr, boolean wait)
    {
        return new IpcConnecter(ioThread, session, options, addr, wait);
    }

    @Override
    public Listener createListener(IOThread ioThread, SocketBase socket, Options options)
    {
        return new IpcListener(ioThread, socket, options);
    }
}
