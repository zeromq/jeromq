package zmq.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;

import zmq.io.net.Address;
import zmq.io.net.tcp.TcpUtils;

public class Utils
{
    private Utils()
    {
    }

    private static final SecureRandom random = new SecureRandom();

    public static int randomInt()
    {
        return random.nextInt();
    }

    public static byte[] randomBytes(int length)
    {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public static int findOpenPort() throws IOException
    {
        final ServerSocket tmpSocket = new ServerSocket(0, 0);
        try {
            return tmpSocket.getLocalPort();
        }
        finally {
            tmpSocket.close();
        }
    }

    public static void unblockSocket(SelectableChannel... channels) throws IOException
    {
        TcpUtils.unblockSocket(channels);
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

    public static Address getPeerIpAddress(SocketChannel fd)
    {
        SocketAddress address = fd.socket().getRemoteSocketAddress();

        return new Address(address);
    }

    public static String dump(ByteBuffer buffer, int pos, int limit)
    {
        int oldpos = buffer.position();
        int oldlimit = buffer.limit();
        buffer.limit(limit).position(pos);

        StringBuilder builder = new StringBuilder("[");
        for (int idx = buffer.position(); idx < buffer.limit(); ++idx) {
            builder.append(buffer.get(idx));
            builder.append(',');
        }
        builder.append(']');

        buffer.limit(oldlimit).position(oldpos);
        return builder.toString();
    }
}
