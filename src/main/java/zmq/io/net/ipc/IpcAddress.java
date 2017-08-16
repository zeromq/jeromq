package zmq.io.net.ipc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import zmq.io.net.Address;
import zmq.io.net.ProtocolFamily;
import zmq.io.net.StandardProtocolFamily;
import zmq.io.net.tcp.TcpAddress;

public class IpcAddress implements Address.IZAddress
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
    private final SocketAddress     sourceAddress;

    public IpcAddress(String addr)
    {
        String[] strings = addr.split(";");

        address = resolve(strings[0], false, false);
        if (strings.length == 2 && !"".equals(strings[1])) {
            sourceAddress = resolve(strings[1], false, false);
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
    public InetSocketAddress resolve(String name, boolean ipv6, boolean local)
    {
        this.name = name;

        int hash = name.hashCode();
        if (hash < 0) {
            hash = -hash;
        }
        hash = hash % 55536;
        hash += 10000;

        try {
            return new InetSocketAddress(InetAddress.getByName(null), hash);
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
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

    @Override
    public SocketAddress sourceAddress()
    {
        return sourceAddress;
    }
}
