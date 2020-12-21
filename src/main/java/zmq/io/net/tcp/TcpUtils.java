package zmq.io.net.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
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
    private static final class SocketOptionsProvider
    {
        // Wrapped in a inner class, to avoid the @SuppressWarnings for the whole class
        private static final SocketOption<Integer> TCP_KEEPCOUNT;
        private static final SocketOption<Integer> TCP_KEEPIDLE;
        private static final SocketOption<Integer> TCP_KEEPINTERVAL;
        private static final boolean WITH_EXTENDED_KEEPALIVE;
        static {
            SocketOption<Integer> tryCount = null;
            SocketOption<Integer> tryIdle = null;
            SocketOption<Integer> tryInterval = null;

            boolean extendedKeepAlive = false;
            try {
                Class<?> eso = Options.class.getClassLoader().loadClass("jdk.net.ExtendedSocketOptions");
                tryCount = (SocketOption<Integer>) eso.getField("TCP_KEEPCOUNT").get(null);
                tryIdle = (SocketOption<Integer>) eso.getField("TCP_KEEPIDLE").get(null);
                tryInterval = (SocketOption<Integer>) eso.getField("TCP_KEEPINTERVAL").get(null);
                extendedKeepAlive = true;
            }
            catch (Throwable e) {
            }
            TCP_KEEPCOUNT = tryCount;
            TCP_KEEPIDLE = tryIdle;
            TCP_KEEPINTERVAL = tryInterval;
            WITH_EXTENDED_KEEPALIVE = extendedKeepAlive;
        }
        @SuppressWarnings("restriction")
        private static void conditionnalSet(Socket socket, SocketOption<Integer> option, int value) throws IOException
        {
            if (value >= 0) {
                jdk.net.Sockets.setOption(socket, option, value);
            }
        }
    }

    private static interface OptionSetter<S>
    {
        void setOption(S socket) throws IOException;
    }

    private TcpUtils()
    {
    }

    // The explicit IOException is useless, but kept for API compatibility
    public static void tuneTcpSocket(SocketChannel channel) throws IOException
    {
        // Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        // so using Nagle wouldn't improve throughput in anyway, but it would
        // hurt latency.
        setOption(channel, socket -> socket.setTcpNoDelay(true));
    }

    public static void tuneTcpKeepalives(SocketChannel channel, int tcpKeepAlive, int tcpKeepAliveCnt,
            int tcpKeepAliveIdle, int tcpKeepAliveIntvl)
    {
        setOption(channel, socket -> {
            socket.setKeepAlive(tcpKeepAlive == 1);
            if (WITH_EXTENDED_KEEPALIVE) {
                SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPCOUNT, tcpKeepAliveCnt);
                SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPIDLE, tcpKeepAliveIdle);
                SocketOptionsProvider.conditionnalSet(socket, SocketOptionsProvider.TCP_KEEPINTERVAL, tcpKeepAliveIntvl);
            }
        });
    }

    public static boolean setTcpReceiveBuffer(Channel channel, final int rcvbuf)
    {
        setOption(channel,
                socket -> socket.setReceiveBufferSize(rcvbuf),
                socket -> socket.setReceiveBufferSize(rcvbuf));
        return true;
    }

    public static boolean setTcpSendBuffer(Channel channel, final int sndbuf)
    {
        setOption(channel, socket -> socket.setSendBufferSize(sndbuf));
        return true;
    }

    public static boolean setIpTypeOfService(Channel channel, final int tos)
    {
        setOption(channel, socket -> socket.setTrafficClass(tos));
        return true;
    }

    public static boolean setReuseAddress(Channel channel, final boolean reuse)
    {
        setOption(channel,
                socket -> socket.setReuseAddress(reuse),
                socket -> socket.setReuseAddress(reuse));
        return true;
    }

    private static void setOption(Channel channel, OptionSetter<Socket> setter)
    {
        setOption(channel, setter, s -> { });
    }

    private static void setOption(Channel channel, OptionSetter<Socket> setter, OptionSetter<ServerSocket> serverSetter)
    {
        try {
            if (channel instanceof ServerSocketChannel) {
                serverSetter.setOption(((ServerSocketChannel) channel).socket());
            }
            else if (channel instanceof SocketChannel) {
                setter.setOption(((SocketChannel) channel).socket());
            }
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
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
