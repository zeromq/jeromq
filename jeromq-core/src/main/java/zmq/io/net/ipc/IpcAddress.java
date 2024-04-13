package zmq.io.net.ipc;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;

import zmq.ZMQ;
import zmq.io.net.Address;
import zmq.io.net.ProtocolFamily;
import zmq.io.net.StandardProtocolFamily;
import zmq.io.net.tcp.TcpAddress;
import zmq.util.Utils;

public class IpcAddress implements Address.IZAddress<InetSocketAddress>
{
    public static class IpcAddressMask extends TcpAddress
    {
        public IpcAddressMask(String addr, boolean ipv6)
        {
            super(addr, ipv6);
        }

        public boolean matchAddress(SocketAddress addr)
        {
            return address().equals(addr);
        }
    }

    private String                  name;
    private final InetSocketAddress address;
    private final InetSocketAddress sourceAddress;

    public IpcAddress(String addr)
    {
        String[] strings = addr.split(";");

        address = resolve(strings[0], ZMQ.PREFER_IPV6, true);
        if (strings.length == 2 && !"".equals(strings[1])) {
            sourceAddress = resolve(strings[1], ZMQ.PREFER_IPV6, true);
        }
        else {
            sourceAddress = null;
        }
    }

    @Override
    public String toString()
    {
        if (name == null) {
            return "";
        }

        return "ipc://" + name;
    }

    @Override
    public String toString(int port)
    {
        if ("*".equals(name)) {
            String suffix = Utils.unhash(port - 10000);
            return "ipc://" + suffix;
        }
        return toString();
    }

    @Override
    public InetSocketAddress resolve(String name, boolean ipv6, boolean loopback)
    {
        this.name = name;

        int hash = name.hashCode();
        if ("*".equals(name)) {
            hash = 0;
        }
        else {
            if (hash < 0) {
                hash = -hash;
            }
            hash = hash % 55536;
            hash += 10000;
        }

        return new InetSocketAddress(findAddress(ipv6, loopback), hash);
    }

    @Override
    public InetSocketAddress address()
    {
        return address;
    }

    @Override
    public ProtocolFamily family()
    {
        return StandardProtocolFamily.INET;
    }

    @Override
    public InetSocketAddress sourceAddress()
    {
        return sourceAddress;
    }

    private InetAddress findAddress(boolean ipv6, boolean loopback)
    {
        Class<? extends InetAddress> addressClass = ipv6 ? Inet6Address.class : Inet4Address.class;
        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface
                    .getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
                NetworkInterface net = interfaces.nextElement();
                for (Enumeration<InetAddress> addresses = net.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (inetAddress.isLoopbackAddress() == loopback && addressClass.isInstance(inetAddress)) {
                        return inetAddress;
                    }
                }
            }
        }
        catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException("no address found " + (ipv6 ? "IPV6" : "IPV4") + (loopback ? "local" : ""));
    }
}
