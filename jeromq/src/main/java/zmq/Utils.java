package zmq;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import zmq.io.net.Address;
import zmq.io.net.tcp.TcpUtils;

@Deprecated
public class Utils
{
    private Utils()
    {
    }

    public static int randomInt()
    {
        return zmq.util.Utils.randomInt();
    }

    public static byte[] randomBytes(int length)
    {
        return zmq.util.Utils.randomBytes(length);
    }

    public static int findOpenPort() throws IOException
    {
        return zmq.util.Utils.findOpenPort();
    }

    public static void unblockSocket(SelectableChannel... channels) throws IOException
    {
        TcpUtils.unblockSocket(channels);
    }

    public static <T> T[] realloc(Class<T> klass, T[] src, int size, boolean ended)
    {
        return zmq.util.Utils.realloc(klass, src, size, ended);
    }

    public static byte[] bytes(ByteBuffer buf)
    {
        return zmq.util.Utils.bytes(buf);
    }

    public static byte[] realloc(byte[] src, int size)
    {
        return zmq.util.Utils.realloc(src, size);
    }

    public static boolean delete(File path)
    {
        return zmq.util.Utils.delete(path);
    }

    public static Address getPeerIpAddress(SocketChannel fd)
    {
        return zmq.util.Utils.getPeerIpAddress(fd);
    }
}
