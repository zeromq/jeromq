package zmq;

import java.io.File;
import java.io.FileNotFoundException;
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
    
    public static void tune_tcp_socket(Socket fd) throws SocketException {
        //  Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        //  so using Nagle wouldn't improve throughput in anyway, but it would
        //  hurt latency.
        
        fd.setTcpNoDelay(true);

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

        if (tcp_keepalive != -1) {
            fd.setKeepAlive(true);
        }
    }
    
    public static void unblock_socket(SelectableChannel s) throws IOException {
        s.configureBlocking(false);
    }
    
    public static <T> T[] realloc(Class<T> klass, T[] src, int size) {
        return realloc(klass, src, size, true);
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
                System.arraycopy(src, 0, dest, 0, size);
            else
                System.arraycopy(src, src.length - size, dest, 0, size);

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

    public static void memcpy(byte[] dest, byte[] src, int size) {
        memcpy(dest, 0, src, 0, size);
    }
    
    public static void memcpy(byte[] dest, String src) {
        memcpy(dest, 0, src!=null?src.getBytes():null, 0, src!=null?src.length():0);
    }

    public static void memcpy(byte[] dest, int to, byte[] src, int from, int size) {
        if (src == null || dest == null)
            return;
        
        System.arraycopy(src, from, dest, to, size);
    }

    public static void memcpy(ByteBuffer dest, byte[] src, int size) {
        memcpy(dest, 0, src, 0, size);
    }
    
    public static void memcpy(ByteBuffer dest, String src) {
        memcpy(dest, 0, src!=null?src.getBytes():null, 0, src!=null?src.length():0);
    }

    public static void memcpy(ByteBuffer dest, int to, byte[] src, int from, int size) {
        if (src == null || dest == null)
            return;
        
        dest.position(to);
        dest.put(src, from, size);
    }

    public static void memcpy(ByteBuffer dest, ByteBuffer src) {
        if (dest.remaining() >= src.remaining()) {
            dest.put(src);
        } else {
            while (dest.hasRemaining())
                dest.put(src.get()); 
        }
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
