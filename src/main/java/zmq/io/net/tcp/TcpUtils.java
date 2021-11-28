package zmq.io.net.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import zmq.ZError;
import zmq.io.net.Address;

public class TcpUtils
{
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
        setOption(channel,
                socket -> socket.setTcpNoDelay(true));
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
        setOption(channel,
                socket -> socket.setSendBufferSize(sndbuf));
        return true;
    }

    public static boolean setIpTypeOfService(Channel channel, final int tos)
    {
        setOption(channel,
                socket -> socket.setTrafficClass(tos));
        return true;
    }

    public static boolean setReuseAddress(Channel channel, final boolean reuse)
    {
        setOption(channel,
                socket -> socket.setReuseAddress(reuse),
                socket -> socket.setReuseAddress(reuse));
        return true;
    }

    public static void tuneTcpKeepalives(SocketChannel channel, int tcpKeepAlive, int tcpKeepAliveCnt,
            int tcpKeepAliveIdle, int tcpKeepAliveIntvl)
    {
        setOption(channel,
                socket -> socket.setKeepAlive(tcpKeepAlive == 1));
   }

    /**
     * A single setter method, used when the option doesn't apply to a {@link ServerSocket}
     * @param channel
     * @param setter
     */
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
