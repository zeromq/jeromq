package zmq.io.net.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.ZError;
import zmq.io.net.Address;

public class TcpUtils
{
    public static final boolean WITH_EXTENDED_KEEPALIVE = SocketOptionsProvider.WITH_EXTENDED_KEEPALIVE;

    @SuppressWarnings("unchecked")
    private static final class SocketOptionsProvider {
        // Wrapped in a inner class, to avoid the @SuppressWarnings for the whole class
        private static final SocketOption<Integer> TCP_KEEPCOUNT;
        private static final SocketOption<Integer> TCP_KEEPIDLE;
        private static final SocketOption<Integer> TCP_KEEPINTERVAL;
        private static final boolean WITH_EXTENDED_KEEPALIVE;
        static {
            SocketOption<Integer> try_TCP_KEEPCOUNT = null;
            SocketOption<Integer> try_TCP_KEEPIDLE = null;
            SocketOption<Integer> try_TCP_KEEPINTERVAL = null;

            boolean extendedKeepAlive = false;
            try {
                Class<?> eso = Options.class.getClassLoader().loadClass("jdk.net.ExtendedSocketOptions");
                try_TCP_KEEPCOUNT = (SocketOption<Integer>) eso.getField("TCP_KEEPCOUNT").get(null);
                try_TCP_KEEPIDLE = (SocketOption<Integer>) eso.getField("TCP_KEEPIDLE").get(null);
                try_TCP_KEEPINTERVAL = (SocketOption<Integer>) eso.getField("TCP_KEEPINTERVAL").get(null);
                extendedKeepAlive = true;
            } catch (Throwable e) {
            }
            TCP_KEEPCOUNT = try_TCP_KEEPCOUNT;
            TCP_KEEPIDLE = try_TCP_KEEPIDLE;
            TCP_KEEPINTERVAL = try_TCP_KEEPINTERVAL;
            WITH_EXTENDED_KEEPALIVE = extendedKeepAlive;
        }
        @SuppressWarnings("restriction")
        private static void conditionnalSet(Socket socket, SocketOption<Integer> option, int value) throws IOException {
            if (value >= 0) {
                jdk.net.Sockets.setOption(socket, option, value);
            }
        }
    }

    private static interface OptionSetter
    {
        boolean setOption(Socket socket) throws SocketException;

        boolean setOption(ServerSocket socket) throws SocketException;
    }

    private abstract static class SocketOptionSetter implements OptionSetter
    {
        @Override
        public boolean setOption(ServerSocket socket) throws SocketException
        {
            return false;
        }
    }

    private TcpUtils()
    {
    }

    public static void tuneTcpSocket(SocketChannel channel) throws IOException
    {
        // Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        // so using Nagle wouldn't improve throughput in anyway, but it would
        // hurt latency.
        setOption(channel, new SocketOptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setTcpNoDelay(true);
                return true;
            }
        });
    }

    public static void tuneTcpKeepalives(SocketChannel channel, int tcpKeepAlive, int tcpKeepAliveCnt,
                                         int tcpKeepAliveIdle, int tcpKeepAliveIntvl)
            throws IOException
    {
        final boolean keepAlive = tcpKeepAlive == 1;
        final int KeepAliveCnt = tcpKeepAliveCnt;
        final int KeepAliveIdle = tcpKeepAliveIdle;
        final int KeepAliveIntvl = tcpKeepAliveIntvl;
        setOption(channel, new SocketOptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setKeepAlive(keepAlive);
                if (WITH_EXTENDED_KEEPALIVE) {
                    try {
                        SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPCOUNT, KeepAliveCnt);
                        SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPIDLE, KeepAliveIdle);
                        SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPINTERVAL, KeepAliveIntvl);
                    } catch (IOException e) {
                        throw new SocketException(e.getMessage());
                    }
                }
                return true;
            }
        });
    }

    public static boolean setTcpReceiveBuffer(Channel channel, final int rcvbuf)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setReceiveBufferSize(rcvbuf);
                return true;
            }

            @Override
            public boolean setOption(ServerSocket socket) throws SocketException
            {
                socket.setReceiveBufferSize(rcvbuf);
                return true;
            }
        });
    }

    public static boolean setTcpSendBuffer(Channel channel, final int sndbuf)
    {
        return setOption(channel, new SocketOptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setSendBufferSize(sndbuf);
                return true;
            }
        });
    }

    public static boolean setIpTypeOfService(Channel channel, final int tos)
    {
        return setOption(channel, new SocketOptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setTrafficClass(tos);
                return true;
            }
        });
    }

    public static boolean setReuseAddress(Channel channel, final boolean reuse)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public boolean setOption(Socket socket) throws SocketException
            {
                socket.setReuseAddress(reuse);
                return true;
            }

            @Override
            public boolean setOption(ServerSocket socket) throws SocketException
            {
                socket.setReuseAddress(reuse);
                return true;
            }
        });
    }

    private static boolean setOption(Channel channel, OptionSetter setter)
    {
        try {
            if (channel instanceof ServerSocketChannel) {
                return setter.setOption(((ServerSocketChannel) channel).socket());
            }
            else if (channel instanceof SocketChannel) {
                return setter.setOption(((SocketChannel) channel).socket());
            }
        }
        catch (SocketException e) {
            throw new ZError.IOException(e);
        }
        return false;
    }

    public static void unblockSocket(SelectableChannel... channels) throws IOException
    {
        for (SelectableChannel ch : channels) {
            ch.configureBlocking(false);
        }
    }

    public static void enableIpv4Mapping(SelectableChannel channel)
    {
        // TODO V4 enable ipv4 mapping
    }

    public static Address getPeerIpAddress(SocketChannel channel)
    {
        SocketAddress address = channel.socket().getRemoteSocketAddress();

        return new Address(address);
    }
}
