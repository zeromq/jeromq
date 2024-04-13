package zmq.io.net.ipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.AbstractSocketListener;

public class IpcListener extends AbstractSocketListener<UnixDomainSocketAddress, IpcAddress>
{
    // bind will create this socket file but close will not remove it, so we need to do that ourselves on close.
    private Path boundSocketPath;

    public IpcListener(IOThread ioThread, SocketBase socket, Options options)
    {
        super(ioThread, socket, options);
    }

    @Override
    public String getAddress()
    {
        return getZAddress().toString(-1);
    }

    //  Set address to listen on.
    @Override
    public boolean setAddress(String addr)
    {
        return super.setZAddress(new IpcAddress(addr));
    }

    @Override
    protected ServerSocketChannel openServer(IpcAddress address) throws IOException
    {
        if (options.selectorChooser == null) {
            return ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        }
        else {
            return options.selectorChooser.choose(address, options).openServerSocketChannel(StandardProtocolFamily.UNIX);
        }
    }

    @Override
    protected void bindServer(ServerSocketChannel fd, IpcAddress address) throws IOException
    {
        fd.configureBlocking(false);

        UnixDomainSocketAddress socketAddress = address.address();
        fd.bind(socketAddress, options.backlog);

        assert (boundSocketPath == null);
        boundSocketPath = socketAddress.getPath();
    }

    @Override
    protected SocketChannel accept(ServerSocketChannel fd) throws IOException
    {
        return fd.accept();
    }

    @Override
    protected void tuneAcceptedChannel(SocketChannel channel)
    {
        // no-op
    }

    @Override
    protected void closeServerChannel(ServerSocketChannel fd) throws IOException
    {
        try {
            fd.close();
        }
        finally {
            Path socketPath = this.boundSocketPath;
            this.boundSocketPath = null;
            if (socketPath != null) {
                Files.deleteIfExists(socketPath);
            }
        }
    }
}
