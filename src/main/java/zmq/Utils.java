package zmq;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;

public class Utils
{
    private Utils()
    {
    }

    private static SecureRandom random = new SecureRandom();

    public static int generateRandom()
    {
        return random.nextInt();
    }

    public static int findOpenPort() throws IOException
    {
        ServerSocket tmpSocket = new ServerSocket(0);
        int portNumber = tmpSocket.getLocalPort();
        tmpSocket.close();
        return portNumber;
    }

    public static void tuneTcpSocket(SocketChannel ch) throws SocketException
    {
        tuneTcpSocket(ch.socket());
    }

    public static void tuneTcpSocket(Socket fd) throws SocketException
    {
        //  Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        //  so using Nagle wouldn't improve throughput in anyway, but it would
        //  hurt latency.
        try {
            fd.setTcpNoDelay(true);
        }
        catch (SocketException e) {
        }
    }

    public static void tuneTcpKeepalives(SocketChannel ch, int tcpKeepalive,
                                         int tcpKeepaliveCnt, int tcpKeepaliveIdle,
                                         int tcpKeepaliveIntvl) throws SocketException
    {
        tuneTcpKeepalives(ch.socket(), tcpKeepalive, tcpKeepaliveCnt,
                tcpKeepaliveIdle, tcpKeepaliveIntvl);
    }

    public static void tuneTcpKeepalives(Socket fd, int tcpKeepalive,
            int tcpKeepaliveCnt, int tcpKeepaliveIdle,
            int tcpKeepaliveIntvl) throws SocketException
    {
        if (tcpKeepalive == 1) {
            fd.setKeepAlive(true);
        }
        else if (tcpKeepalive == 0) {
            fd.setKeepAlive(false);
        }
    }

    public static void unblockSocket(SelectableChannel s) throws IOException
    {
        s.configureBlocking(false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] realloc(Class<T> klass, T[] src, int size, boolean ended)
    {
        T[] dest;

        if (size > src.length) {
            dest = (T[]) Array.newInstance(klass, size);
            if (ended) {
                System.arraycopy(src, 0, dest, 0, src.length);
            }
            else {
                System.arraycopy(src, 0, dest, size - src.length, src.length);
            }
        }
        else if (size < src.length) {
            dest = (T[]) Array.newInstance(klass, size);
            if (ended) {
                System.arraycopy(src, src.length - size, dest, 0, size);
            }
            else {
                System.arraycopy(src, 0, dest, 0, size);
            }
        }
        else {
            dest = src;
        }
        return dest;
    }

    public static byte[] bytes(ByteBuffer buf)
    {
        byte[] d = new byte[buf.limit()];
        buf.get(d);
        return d;
    }

    public static byte[] realloc(byte[] src, int size)
    {
        byte[] dest = new byte[size];
        if (src != null) {
            System.arraycopy(src, 0, dest, 0, src.length);
        }

        return dest;
    }

    public static boolean delete(File path)
    {
        if (!path.exists()) {
            return false;
        }
        boolean ret = true;
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File f : files) {
                    ret = ret && delete(f);
                }
            }
        }
        return ret && path.delete();
    }
}
