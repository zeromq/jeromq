package zmq.io.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.AbstractSocketConnecter;
import zmq.io.net.Address;
import zmq.io.net.StandardProtocolFamily;

public class TcpConnecter extends AbstractSocketConnecter<InetSocketAddress>
{
    public TcpConnecter(IOThread ioThread, SessionBase session, Options options, Address<InetSocketAddress> addr,
            boolean delayedStart)
    {
        super(ioThread, session, options, addr, delayedStart);
    }

    @Override
    protected SocketChannel openClient(Address.IZAddress<InetSocketAddress> address) throws IOException
    {
        SocketChannel fd;
        if (options.selectorChooser == null) {
            fd = SocketChannel.open();
        }
        else {
            fd = options.selectorChooser.choose(address, options).openSocketChannel();
        }

        // On some systems, IPv4 mapping in IPv6 sockets is disabled by default.
        // Switch it on in such cases.
        // The method enableIpv4Mapping is empty. Still to be written
        if (address.family() == StandardProtocolFamily.INET6) {
            TcpUtils.enableIpv4Mapping(fd);
        }

        // Set the socket to non-blocking mode so that we get async connect().
        fd.configureBlocking(false);

        //  Set the socket buffer limits for the underlying socket.
        if (options.sndbuf != 0) {
            TcpUtils.setTcpSendBuffer(fd, options.sndbuf);
        }
        if (options.rcvbuf != 0) {
            TcpUtils.setTcpReceiveBuffer(fd, options.rcvbuf);
        }

        // Set the IP Type-Of-Service priority for this socket
        if (options.tos != 0) {
            TcpUtils.setIpTypeOfService(fd, options.tos);
        }
        return fd;
    }

    @Override
    protected void tuneConnectedChannel(SocketChannel channel) throws IOException
    {
        TcpUtils.tuneTcpSocket(channel);
        TcpUtils.tuneTcpKeepalives(
                channel,
                options.tcpKeepAlive,
                options.tcpKeepAliveCnt,
                options.tcpKeepAliveIdle,
                options.tcpKeepAliveIntvl);
    }
}
