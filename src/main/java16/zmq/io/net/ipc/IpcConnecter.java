package zmq.io.net.ipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.AbstractSocketConnecter;
import zmq.io.net.Address;

public class IpcConnecter extends AbstractSocketConnecter
{
    public IpcConnecter(IOThread ioThread, SessionBase session, final Options options, final Address addr, boolean wait)
    {
        super(ioThread, session, options, addr, wait);
    }

    @Override
    protected SocketChannel openClient(Address.IZAddress address) throws IOException
    {
        SocketChannel fd = SocketChannel.open(StandardProtocolFamily.UNIX);
        fd.configureBlocking(false);
        return fd;
    }

    @Override
    protected void tuneConnectedChannel(SocketChannel channel) throws IOException
    {
        // no-op
    }
}
