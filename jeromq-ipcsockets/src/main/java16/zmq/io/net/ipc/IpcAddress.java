package zmq.io.net.ipc;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import org.zeromq.ZMQException;

import zmq.ZError;
import zmq.io.net.Address;
import zmq.io.net.ProtocolFamily;
import zmq.io.net.StandardProtocolFamily;

public class IpcAddress implements Address.IZAddress<UnixDomainSocketAddress>
{
    private final UnixDomainSocketAddress address;

    public IpcAddress(String addr)
    {
        this.address = resolve(addr);
    }

    @Override
    public String toString()
    {
        // Always the resolved path, so a wildcard bind reports the actual socket file
        return "ipc://" + address.getPath();
    }

    @Override
    public ProtocolFamily family()
    {
        return StandardProtocolFamily.UNIX;
    }

    @Override
    public String toString(int port)
    {
        return toString();
    }

    @Override
    public UnixDomainSocketAddress resolve(String name, boolean ipv6, boolean local)
    {
        return resolve(name);
    }

    private UnixDomainSocketAddress resolve(String name)
    {
        if (!"*".equals(name)) {
            return UnixDomainSocketAddress.of(name);
        }

        try {
            Path temp = Files.createTempFile("zmq-", ".sock");
            Files.delete(temp);
            return UnixDomainSocketAddress.of(temp);
        }
        catch (IOException e) {
            throw new ZMQException(e.getMessage(), ZError.EADDRNOTAVAIL, e);
        }
    }

    @Override
    public UnixDomainSocketAddress address()
    {
        return address;
    }

    @Override
    public UnixDomainSocketAddress sourceAddress()
    {
        // Unix domain sockets have no source address to bind the connecting side to
        return null;
    }
}
