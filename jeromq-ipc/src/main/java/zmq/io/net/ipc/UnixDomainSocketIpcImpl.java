package zmq.io.net.ipc;

import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.Listener;

public class UnixDomainSocketIpcImpl implements IpcImpl
{
    @Override
    public boolean isImplementedViaTcpLoopback()
    {
        return false;
    }

    @Override
    public Address.IZAddress createAddress(String addr)
    {
        return new IpcAddress(addr);
    }

    @Override
    public boolean isIpcAddress(SocketAddress address)
    {
        return address instanceof UnixDomainSocketAddress;
    }

    @Override
    public String getAddress(SocketAddress address)
    {
        assert (isIpcAddress(address));
        var unixDomainSocketAddress = (UnixDomainSocketAddress) address;
        return unixDomainSocketAddress.getPath().toString();
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
