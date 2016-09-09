package zmq;

import java.net.InetSocketAddress;

// fake Unix domain socket
public class IpcListener extends TcpListener
{
    private final IpcAddress address;

    public IpcListener(IOThread ioThread, SocketBase socket, final Options options)
    {
        super(ioThread, socket, options);

        address = new IpcAddress();
    }

    // Get the bound address for use with wildcards
    public String getAddress()
    {
        return address.toString();
    }

    //  Set address to listen on.
    public int setAddress(String addr)
    {
        address.resolve(addr, false);

        InetSocketAddress sock = (InetSocketAddress) address.address();
        String fake = sock.getAddress().getHostAddress() + ":" + sock.getPort();
        return super.setAddress(fake);
    }
}
