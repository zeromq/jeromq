/*
    Copyright other contributors as noted in the AUTHORS file.

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

public class Utils {

    private static SecureRandom random = new SecureRandom();
    
    public static int generate_random() {
        return random.nextInt();
    }

    public static void tune_tcp_socket(SocketChannel ch) throws SocketException {
        tune_tcp_socket(ch.socket());
    }
    
    public static void tune_tcp_socket(Socket fd) throws SocketException 
    {
        //  Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        //  so using Nagle wouldn't improve throughput in anyway, but it would
        //  hurt latency.
        try {
            fd.setTcpNoDelay (true);
        } catch (SocketException e) {
        }
    }

    
    public static void tune_tcp_keepalives(SocketChannel ch, int tcp_keepalive,
            int tcp_keepalive_cnt, int tcp_keepalive_idle,
            int tcp_keepalive_intvl) throws SocketException {

        tune_tcp_keepalives(ch.socket(), tcp_keepalive, tcp_keepalive_cnt,
                tcp_keepalive_idle, tcp_keepalive_intvl);
    }
    
    public static void tune_tcp_keepalives(Socket fd, int tcp_keepalive,
            int tcp_keepalive_cnt, int tcp_keepalive_idle,
            int tcp_keepalive_intvl) throws SocketException {

        if (tcp_keepalive == 1) {
            fd.setKeepAlive(true);
        } else if (tcp_keepalive == 0) {
            fd.setKeepAlive(false);
        }
    }
    
    public static void unblock_socket(SelectableChannel s) throws IOException {
        s.configureBlocking(false);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T[] realloc(Class<T> klass, T[] src, int size, boolean ended) {
        T[] dest;
        
        if (size > src.length) {
            dest = (T[])(Array.newInstance(klass, size));
            if (ended)
                System.arraycopy(src, 0, dest, 0, src.length);
            else
                System.arraycopy(src, 0, dest, size-src.length, src.length);
        } else if (size < src.length) {
            dest = (T[])(Array.newInstance(klass, size));
            if (ended)
                System.arraycopy(src, src.length - size, dest, 0, size);
            else
                System.arraycopy(src, 0, dest, 0, size);

        } else {
            dest = src;
        }
        return dest;
    }
    
    public static <T> void swap(List<T> items, int index1_, int index2_) {
        if (index1_ == index2_) 
            return;
                    
        T item1 = items.get(index1_);
        T item2 = items.get(index2_);
        if (item1 != null)
            items.set(index2_, item1);
        if (item2 != null)
            items.set(index1_, item2);
    }

    public static byte[] bytes(ByteBuffer buf) {
        byte[] d = new byte[buf.limit()];
        buf.get(d);
        return d;
    }

    public static byte[] realloc(byte[] src, int size) {

        byte[] dest = new byte[size];
        if (src != null)
            System.arraycopy(src, 0, dest, 0, src.length);
        
        return dest;
    }

    public static boolean delete(File path) {
        if (!path.exists())
            return false; 
        boolean ret = true;
        if (path.isDirectory()){
            for (File f : path.listFiles()){
                ret = ret && delete(f);
            }
        }
        return ret && path.delete();
    }

}
