/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package zmq;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.List;

class Utils
{
    private Utils()
    {
    }

    private static SecureRandom random = new SecureRandom();

    public static int generateRandom()
    {
        return random.nextInt();
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
        tune_tcp_keepalives(ch.socket(), tcpKeepalive, tcpKeepaliveCnt,
                tcpKeepaliveIdle, tcpKeepaliveIntvl);
    }

    public static void tune_tcp_keepalives(Socket fd, int tcpKeepalive,
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

    public static <T> void swap(List<T> items, int index1, int index2)
    {
        if (index1 == index2) {
            return;
        }

        T item1 = items.get(index1);
        T item2 = items.get(index2);
        if (item1 != null) {
            items.set(index2, item1);
        }
        if (item2 != null) {
            items.set(index1, item2);
        }
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
            for (File f : path.listFiles()) {
                ret = ret && delete(f);
            }
        }
        return ret && path.delete();
    }
}
