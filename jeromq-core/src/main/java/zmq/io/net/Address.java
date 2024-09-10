package zmq.io.net;

import java.net.SocketAddress;

public class Address<S extends SocketAddress>
{
    public interface IZAddress<SA extends SocketAddress>
    {
        ProtocolFamily family();

        String toString(int port);

        SA resolve(String name, boolean ipv6, boolean local);

        SA address();

        SA sourceAddress();
    }

    private final NetProtocol protocol;
    private final String      address;

    private IZAddress<S> resolved;

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
        protocol = NetProtocol.findByAddress(socketAddress);
        address = protocol.formatSocketAddress(socketAddress);
        resolved = null;
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

    public IZAddress<S> resolved()
    {
        return resolved;
    }

    public boolean isResolved()
    {
        return resolved != null;
    }

    public IZAddress<S> resolve(boolean ipv6)
    {
        resolved = protocol.zresolve(address, ipv6);
        return resolved;
    }
}
