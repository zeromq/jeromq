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

import zmq.ZError;
import zmq.io.net.Address;
import zmq.io.net.tcp.TcpUtils;
import zmq.util.function.Supplier;

public class Utils
{
    private static final ThreadLocal<SecureRandom> random = ThreadLocal.withInitial(SecureRandom::new);

    private Utils()
    {
    }

    public static int randomInt()
    {
        return random.get().nextInt();
    }

    public static int randomInt(int bound)
    {
        return random.get().nextInt(bound);
    }

    public static byte[] randomBytes(int length)
    {
        byte[] bytes = new byte[length];
        random.get().nextBytes(bytes);
        return bytes;
    }

    /**
     * Finds a string whose hashcode is the number in input.
     *
     * @param port the port to find String hashcode-equivalent of. Has to be positive or 0.
     * @return a String whose hashcode is the number in input.
     */
    public static String unhash(int port)
    {
        return unhash(new StringBuilder(), port, 'z').toString();
    }

    private static StringBuilder unhash(StringBuilder builder, int port, char boundary)
    {
        int div = port / 31;
        int remainder = port % 31;
        if (div <= boundary) {
            if (div != 0) {
                builder.append((char) div);
            }
        }
        else {
            unhash(builder, div, boundary);
        }
        builder.append((char) remainder);
        return builder;
    }

    public static int findOpenPort() throws IOException
    {
        try (ServerSocket tmpSocket = new ServerSocket(0, 0)) {
            return tmpSocket.getLocalPort();
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

    /**
     * Resolve the remote address of the channel.
     * @param fd the channel, should be a TCP socket channel
     * @return a new {@link Address}
     * @throws ZError.IOException if the channel is closed or an I/O errors occurred
     * @throws IllegalArgumentException if the SocketChannel is not a TCP channel
     */
    public static Address getPeerIpAddress(SocketChannel fd)
    {
        try {
            SocketAddress address = fd.getRemoteAddress();
            return new Address(address);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    /**
     * Resolve the local address of the channel.
     * @param fd the channel
     * @return a new {@link Address}
     * @throws ZError.IOException if the channel is closed or an I/O errors occurred
     * @throws IllegalArgumentException if the SocketChannel is not a TCP channel
     */
    public static <S extends SocketAddress> Address<S> getLocalIpAddress(SocketChannel fd)
    {
        try {
            S address = (S) fd.getLocalAddress();
            return new Address<>(address);
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
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

    public static void checkArgument(boolean expression, String errorMessage)
    {
        checkArgument(expression, () -> errorMessage);
    }

    public static void checkArgument(boolean expression, Supplier<String> errorMessage)
    {
        if (!expression) {
            throw new IllegalArgumentException(errorMessage.get());
        }
    }
}
