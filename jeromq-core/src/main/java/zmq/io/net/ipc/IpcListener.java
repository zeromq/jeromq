package zmq.io.net.ipc;

import java.net.InetSocketAddress;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.tcp.TcpListener;

// fake Unix domain socket
public class IpcListener extends TcpListener
{
    private IpcAddress address;

    public IpcListener(IOThread ioThread, SocketBase socket, Options options)
    {
        super(ioThread, socket, options);

    }

    // Get the bound address for use with wildcards
    @Override
    public String getAddress()
    {
        if (address.address().getPort() == 0) {
            return address(address);
        }
        else {
            return address.toString();
        }
    }

    //  Set address to listen on.
    @Override
    public boolean setAddress(String addr)
    {
        address = new IpcAddress(addr);

        InetSocketAddress sock = address.address();
        return setAddress(sock);
    }
}
