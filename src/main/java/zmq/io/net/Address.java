package zmq.io.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import zmq.io.net.ipc.IpcAddress;
import zmq.io.net.tcp.TcpAddress;

public class Address
{
    public interface IZAddress
    {
        ProtocolFamily family();

        @Override
        String toString();

        InetSocketAddress resolve(String name, boolean ipv6, boolean local);

        SocketAddress address();

        SocketAddress sourceAddress();
    }

    private final NetProtocol protocol;
    private final String      address;
    //    private final boolean ipv4only;

    private IZAddress resolved;

    public Address(final String protocol, final String address)
    {
        this.protocol = NetProtocol.getProtocol(protocol);
        this.address = address;
        resolved = null;
    }

    public Address(SocketAddress socketAddress)
    {
        InetSocketAddress sockAddr = (InetSocketAddress) socketAddress;
        this.address = sockAddr.getAddress().getHostAddress() + ":" + sockAddr.getPort();
        protocol = NetProtocol.tcp;
        resolved = null;
        //        ipv4only = !(sockAddr.getAddress() instanceof Inet6Address);
    }

    @Override
    public String toString()
    {
        if (NetProtocol.tcp == protocol && isResolved()) {
            return resolved.toString();
        }
        else if (NetProtocol.ipc == protocol && isResolved()) {
            return resolved.toString();
        }
        else if (protocol != null && !address.isEmpty()) {
            return protocol.name() + "://" + address;
        }
        else {
            return "";
        }
    }

    public NetProtocol protocol()
    {
        return protocol;
    }

    public String address()
    {
        return address;
    }

    public String host()
    {
        final int portDelimiter = address.lastIndexOf(':');
        if (portDelimiter > 0) {
            return address.substring(0, portDelimiter);
        }
        return address;
    }

    public IZAddress resolved()
    {
        return resolved;
    }

    public boolean isResolved()
    {
        return resolved != null;
    }

    public IZAddress resolve(boolean ipv6)
    {
        if (NetProtocol.tcp.equals(protocol)) {
            resolved = new TcpAddress(address, ipv6);
            return resolved;
        }
        else if (NetProtocol.ipc.equals(protocol)) {
            resolved = new IpcAddress(address);
            return resolved;
        }
        else {
            return null;
        }
    }
}
