package zmq.io.net.tcp;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.zeromq.ZMQException;

import zmq.ZError;
import zmq.io.net.Address;
import zmq.io.net.ProtocolFamily;
import zmq.io.net.StandardProtocolFamily;

public class TcpAddress implements Address.IZAddress
{
    public static class TcpAddressMask extends TcpAddress
    {
        public TcpAddressMask(String addr, boolean ipv6)
        {
            super(addr, ipv6);
        }

        public boolean matchAddress(SocketAddress addr)
        {
            return address().equals(addr);
        }
    }

    private final InetSocketAddress address;
    private final SocketAddress     sourceAddress;

    public TcpAddress(String addr, boolean ipv6)
    {
        String[] strings = addr.split(";");

        address = resolve(strings[0], ipv6, false);
        if (strings.length == 2 && !"".equals(strings[1])) {
            sourceAddress = resolve(strings[1], ipv6, false);
        }
        else {
            sourceAddress = null;
        }
    }

    @Override
    public ProtocolFamily family()
    {
        if (address.getAddress() instanceof Inet6Address) {
            return StandardProtocolFamily.INET6;
        }
        return StandardProtocolFamily.INET;
    }

    // The opposite to resolve()
    @Override
    public String toString()
    {
        return toString(address.getPort());
    }

    // The opposite to resolve()
    @Override
    public String toString(int port)
    {
        if (address == null) {
            return "";
        }

        int addressPort = address.getPort();
        if (addressPort == 0) {
            addressPort = port;
        }
        if (address.getAddress() instanceof Inet6Address) {
            return "tcp://[" + address.getAddress().getHostAddress() + "]:" + addressPort;
        }
        else {
            return "tcp://" + address.getAddress().getHostAddress() + ":" + addressPort;
        }
    }

    /**
     * @param name
     * @param ipv6
     * @param local ignored
     * @return
     * @see zmq.io.net.Address.IZAddress#resolve(java.lang.String, boolean, boolean)
     */
    @Override
    public InetSocketAddress resolve(String name, boolean ipv6, boolean local)
    {
        //  Find the ':' at end that separates address from the port number.
        int delimiter = name.lastIndexOf(':');
        if (delimiter < 0) {
            throw new IllegalArgumentException(name);
        }

        //  Separate the address/port.
        String addrStr = name.substring(0, delimiter);
        String portStr = name.substring(delimiter + 1);

        //  Remove square brackets around the address, if any.
        if (addrStr.length() >= 2 && addrStr.charAt(0) == '[' && addrStr.charAt(addrStr.length() - 1) == ']') {
            addrStr = addrStr.substring(1, addrStr.length() - 1);
        }

        int port;
        //  Allow 0 specifically, to detect invalid port error in atoi if not
        if (portStr.equals("*") || portStr.equals("0")) {
            //  Resolve wildcard to 0 to allow autoselection of port
            port = 0;
        }
        else {
            //  Parse the port number (0 is not a valid port).
            port = Integer.parseInt(portStr);
            if (port == 0) {
                throw new IllegalArgumentException(name);
            }
        }

        InetAddress addrNet = null;

        // '*' as unspecified address is not accepted in Java
        // '::' for IPv6 is accepted
        if (addrStr.equals("*")) {
            addrStr = ipv6 ? "::" : "0.0.0.0";
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(addrStr);
            if (ipv6) {
                // prefer IPv6: return the first ipv6 or the first value if not found
                for (InetAddress addr : addresses) {
                    if (addr instanceof Inet6Address) {
                        addrNet = addr;
                        break;
                    }
                }
                if (addrNet == null) {
                    addrNet = addresses[0];
                }
            }
            else {
                for (InetAddress addr : addresses) {
                    if (addr instanceof Inet4Address) {
                        addrNet = addr;
                        break;
                    }
                }
            }
        }
        catch (UnknownHostException e) {
            throw new ZMQException(e.getMessage(), ZError.EADDRNOTAVAIL, e);
        }

        if (addrNet == null) {
            throw new ZMQException(addrStr + " not found matching IPv4/IPv6 settings", ZError.EADDRNOTAVAIL);
        }

        return new InetSocketAddress(addrNet, port);
    }

    @Override
    public SocketAddress address()
    {
        return address;
    }

    @Override
    public SocketAddress sourceAddress()
    {
        return sourceAddress;
    }
}
