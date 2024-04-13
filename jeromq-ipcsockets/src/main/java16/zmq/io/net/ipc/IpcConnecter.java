package zmq.io.net.ipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.AbstractSocketConnecter;
import zmq.io.net.Address;

public class IpcConnecter extends AbstractSocketConnecter<UnixDomainSocketAddress>
{
    public IpcConnecter(IOThread ioThread, SessionBase session, Options options, Address<UnixDomainSocketAddress> addr, boolean wait)
    {
        super(ioThread, session, options, addr, wait);
    }

    @Override
    protected SocketChannel openClient(Address.IZAddress<UnixDomainSocketAddress> address) throws IOException
    {
        SocketChannel fd;
        if (options.selectorChooser == null) {
            fd = SocketChannel.open(StandardProtocolFamily.UNIX);
        }
        else {
            fd = options.selectorChooser.choose(address, options).openSocketChannel(StandardProtocolFamily.UNIX);
        }
        fd.configureBlocking(false);
        return fd;
    }

    @Override
    protected void tuneConnectedChannel(SocketChannel channel)
    {
        // no-op
    }
}
