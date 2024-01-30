package zmq.io.net.ipc.loopback;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.util.Enumeration;

import zmq.ZMQ;
import zmq.io.net.Address;
import zmq.util.Utils;

public class IpcAddress implements Address.IZAddress
{
    private String                  name;
    private final InetSocketAddress address;

    public IpcAddress(String addr)
    {
        String[] strings = addr.split(";");

        address = resolve(strings[0]);
        if (strings.length == 2 && !"".equals(strings[1])) {
            resolve(strings[1]);
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

    private InetSocketAddress resolve(String name)
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

        return new InetSocketAddress(findAddress(), hash);
    }

    @Override
    public SocketAddress address()
    {
        return address;
    }

    @Override
    public ProtocolFamily family()
    {
        return StandardProtocolFamily.INET;
    }

    private InetAddress findAddress()
    {
        Class<? extends InetAddress> addressClass = ZMQ.PREFER_IPV6 ? Inet6Address.class : Inet4Address.class;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface net = interfaces.nextElement();
                Enumeration<InetAddress> addresses = net.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (inetAddress.isLoopbackAddress() && addressClass.isInstance(inetAddress)) {
                        return inetAddress;
                    }
                }
            }
        }
        catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException("no address found " + (ZMQ.PREFER_IPV6 ? "IPV6" : "IPV4") + "local");
    }
}
