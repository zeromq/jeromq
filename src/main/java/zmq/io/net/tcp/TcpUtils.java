package zmq.io.net.tcp;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import zmq.ZError;
import zmq.io.net.Address;

public class TcpUtils
{
    private static interface OptionSetter
    {
        void setOption(Socket socket) throws SocketException;
    }

    private TcpUtils()
    {
    }

    public static void tuneTcpSocket(SocketChannel channel) throws IOException
    {
        // Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        // so using Nagle wouldn't improve throughput in anyway, but it would
        // hurt latency.
        setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setTcpNoDelay(true);
            }
        });
    }

    public static void tuneTcpKeepalives(SocketChannel channel, int tcpKeepAlive, int tcpKeepAliveCnt,
                                         int tcpKeepAliveIdle, int tcpKeepAliveIntvl)
            throws IOException
    {
        final boolean keepAlive = tcpKeepAlive == 1;
        setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setKeepAlive(keepAlive);
            }
        });
    }

    public static boolean setTcpReceiveBuffer(NetworkChannel channel, final int rcvbuf)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setReceiveBufferSize(rcvbuf);
            }
        });
    }

    public static boolean setTcpSendBuffer(NetworkChannel channel, final int sndbuf)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setSendBufferSize(sndbuf);
            }
        });
    }

    public static boolean setIpTypeOfService(NetworkChannel channel, final int tos)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setTrafficClass(tos);
            }
        });
    }

    public static boolean setReuseAddress(NetworkChannel channel, final boolean reuse)
    {
        return setOption(channel, new OptionSetter()
        {
            @Override
            public void setOption(Socket socket) throws SocketException
            {
                socket.setReuseAddress(reuse);
            }
        });
    }

    private static <T> boolean setOption(NetworkChannel channel, OptionSetter setter)
    {
        if (channel instanceof SocketChannel) {
            try {
                setter.setOption(((SocketChannel) channel).socket());
                return true;
            }
            catch (SocketException e) {
                throw new ZError.IOException(e);
            }
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
