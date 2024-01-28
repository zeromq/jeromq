package zmq.io.net.ipc;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import org.zeromq.ZMQException;

import zmq.ZError;
import zmq.io.net.Address;

public class IpcAddress implements Address.IZAddress
{
    private String                        name;
    private final UnixDomainSocketAddress address;

    public IpcAddress(String addr)
    {
        // TODO inline?
        this.address = this.resolve(addr);
    }

    @Override
    public String toString()
    {
        // TODO possible?
        if (name == null) {
            return "";
        }

        return "ipc://" + this.address.toString();
    }

    @Override
    public String toString(int port)
    {
        // TODO why is port in the interface?
        return toString();
    }

    private UnixDomainSocketAddress resolve(String name)
    {
        this.name = name;

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
    public ProtocolFamily family()
    {
        return StandardProtocolFamily.UNIX;
    }
}
