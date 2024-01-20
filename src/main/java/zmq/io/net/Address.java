package zmq.io.net;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;

public class Address
{
    public interface IZAddress
    {
        ProtocolFamily family();

        String toString(int port);

        // TODO this is really only used internally, the one external use is specific to re-trying local resolution
        // for tcp. Doesn't really belong in this interface.
        //        SocketAddress resolve(String name, boolean ipv6, boolean local);

        SocketAddress address();
    }

    private final NetProtocol protocol;
    private final String      address;

    private IZAddress resolved;

    /**
     * @param protocol
     * @param address
     * @throws IllegalArgumentException if the protocol name can be matched to an actual supported protocol
     */
    @Deprecated
    public Address(final String protocol, final String address)
    {
        this.protocol = NetProtocol.getProtocol(protocol);
        this.address = address;
        resolved = null;
    }

    /**
     * @param protocol
     * @param address
     */
    public Address(final NetProtocol protocol, final String address)
    {
        this.protocol = protocol;
        this.address = address;
        resolved = null;
    }

    /**
     * @param socketAddress
     * @throws IllegalArgumentException if the SocketChannel is not an IP socket address
     */
    public Address(SocketAddress socketAddress)
    {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress sockAddr = (InetSocketAddress) socketAddress;
            this.address = sockAddr.getAddress().getHostAddress() + ":" + sockAddr.getPort();
            protocol = NetProtocol.tcp;
            resolved = null;
        }
        else if (socketAddress instanceof UnixDomainSocketAddress) {
            UnixDomainSocketAddress sockAddr = (UnixDomainSocketAddress) socketAddress;
            this.address = sockAddr.getPath().toString();
            protocol = NetProtocol.ipc;
            resolved = null;
        }
        else {
            throw new IllegalArgumentException("Not a IP socket address");
        }
    }

    @Override
    public String toString()
    {
        if (isResolved()) {
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
        resolved = protocol.zresolve(address, ipv6);
        return resolved;
    }
}
