package zmq.io.net.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import zmq.ZError;
import zmq.io.net.Address;

public class TcpUtils
{
    private TcpUtils()
    {
    }

    public static void tuneTcpSocket(SocketChannel channel) throws IOException
    {
        setOption(channel, StandardSocketOptions.TCP_NODELAY, true);
        //  Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        //  so using Nagle wouldn't improve throughput in anyway, but it would
        //  hurt latency.
        try {
            channel.socket().setTcpNoDelay(true);
        }
        catch (SocketException e) {
        }
    }

    public static void tuneTcpKeepalives(SocketChannel channel, int tcpKeepAlive, int tcpKeepAliveCnt,
                                         int tcpKeepAliveIdle, int tcpKeepAliveIntvl)
            throws IOException
    {
        boolean keepAlive = tcpKeepAlive == 1;
        setOption(channel, StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        try {
            channel.socket().setKeepAlive(keepAlive);
        }
        catch (SocketException e) {
        }
    }

    public static boolean setTcpReceiveBuffer(NetworkChannel channel, int rcvbuf)
    {
        return setOption(channel, StandardSocketOptions.SO_RCVBUF, rcvbuf);
    }

    public static boolean setTcpSendBuffer(NetworkChannel channel, int sndbuf)
    {
        return setOption(channel, StandardSocketOptions.SO_SNDBUF, sndbuf);
    }

    public static boolean setIpTypeOfService(NetworkChannel channel, int tos)
    {
        return setOption(channel, StandardSocketOptions.IP_TOS, tos);
    }

    public static boolean setReuseAddress(NetworkChannel channel, boolean reuse)
    {
        return setOption(channel, StandardSocketOptions.SO_REUSEADDR, reuse);
    }

    private static <T> boolean setOption(NetworkChannel channel, SocketOption<T> option, T value)
    {
        try {
            if (channel.supportedOptions().contains(option)) {
                channel.setOption(option, value);
                return true;
            }
            else {
                return false;
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
