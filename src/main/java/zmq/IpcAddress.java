package zmq;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class IpcAddress implements Address.IZAddress
{
    private String name;
    private InetSocketAddress address;

    @Override
    public String toString()
    {
        if (name == null) {
            return "";
        }

        return "ipc://" + name;
    }

    @Override
    public void resolve(String name, boolean ip4only)
    {
        this.name = name;

        int hash = name.hashCode();
        if (hash < 0) {
            hash = -hash;
        }
        hash = hash % 55536;
        hash += 10000;

        try {
            address = new InetSocketAddress(InetAddress.getByName(null), hash);
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
}
