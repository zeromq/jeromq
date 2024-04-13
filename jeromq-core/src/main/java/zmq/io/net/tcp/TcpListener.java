package zmq.io.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Locale;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.AbstractSocketListener;
import zmq.io.net.Address;
import zmq.io.net.StandardProtocolFamily;

public class TcpListener extends AbstractSocketListener<InetSocketAddress, TcpAddress>
{
    private static final boolean isWindows;
    static {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        isWindows = os.contains("win");
    }

    public TcpListener(IOThread ioThread, SocketBase socket, final Options options)
    {
        super(ioThread, socket, options);
    }

    @Override
    public String getAddress()
    {
        return address(getZAddress());
    }

    protected String address(Address.IZAddress<InetSocketAddress> address)
    {
        int port = getFd().socket().getLocalPort();
        return address.toString(port);
    }

    @Override
    public boolean setAddress(String addr)
    {
        //  Convert the textual address into address structure.
        return setZAddress(new TcpAddress(addr, options.ipv6));
    }

    //  Set address to listen on, used by IpcListener that already resolved the address.
    protected boolean setAddress(InetSocketAddress addr)
    {
        //  Convert the textual address into address structure.
        return setZAddress(new TcpAddress(addr));
    }

    @Override
    protected ServerSocketChannel openServer(TcpAddress address) throws IOException
    {
        if (options.selectorChooser == null) {
            return ServerSocketChannel.open();
        }
        else {
            return options.selectorChooser.choose(address, options).openServerSocketChannel();
        }
    }

    @Override
    protected void bindServer(ServerSocketChannel fd, TcpAddress address) throws IOException
    {
        // On some systems, IPv4 mapping in IPv6 sockets is disabled by default.
        // Switch it on in such cases.
        // The method enableIpv4Mapping is empty. Still to be written
        if (address.family() == StandardProtocolFamily.INET6) {
            TcpUtils.enableIpv4Mapping(fd);
        }

        fd.configureBlocking(false);

        //  Set the socket buffer limits for the underlying socket.
        if (options.sndbuf != 0) {
            TcpUtils.setTcpSendBuffer(fd, options.sndbuf);
        }
        if (options.rcvbuf != 0) {
            TcpUtils.setTcpReceiveBuffer(fd, options.rcvbuf);
        }

        if (!isWindows) {
            TcpUtils.setReuseAddress(fd, true);
        }

        //  Bind the socket to the network interface and port.
        // NB: fd.socket().bind(...) for Android environments
        fd.socket().bind(address.address(), options.backlog);
    }

    @Override
    protected SocketChannel accept(ServerSocketChannel fd) throws IOException
    {
        //  The situation where connection cannot be accepted due to insufficient
        //  resources is considered valid and treated by ignoring the connection.
        //  Accept one connection and deal with different failure modes.
        Address.IZAddress<InetSocketAddress> address = getZAddress();
        assert (fd != null);

        SocketChannel sock = fd.accept();

        if (!options.tcpAcceptFilters.isEmpty()) {
            boolean matched = false;
            for (TcpAddress.TcpAddressMask am : options.tcpAcceptFilters) {
                if (am.matchAddress(address.address())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                try {
                    sock.close();
                }
                catch (IOException ignored) {
                    // Ignored
                }
                return null;
            }
        }
        if (options.tos != 0) {
            TcpUtils.setIpTypeOfService(sock, options.tos);
        }
        //  Set the socket buffer limits for the underlying socket.
        if (options.sndbuf != 0) {
            TcpUtils.setTcpSendBuffer(sock, options.sndbuf);
        }
        if (options.rcvbuf != 0) {
            TcpUtils.setTcpReceiveBuffer(sock, options.rcvbuf);
        }

        if (!isWindows) {
            TcpUtils.setReuseAddress(sock, true);
        }

        return sock;
    }

    @Override
    protected void tuneAcceptedChannel(SocketChannel channel) throws IOException
    {
        TcpUtils.tuneTcpSocket(channel);
        TcpUtils.tuneTcpKeepalives(
                channel,
                options.tcpKeepAlive,
                options.tcpKeepAliveCnt,
                options.tcpKeepAliveIdle,
                options.tcpKeepAliveIntvl);
    }

    @Override
    protected void closeServerChannel(ServerSocketChannel fd) throws IOException
    {
        fd.close();
    }
}
