package zmq;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.List;

public class Utils {

    private static SecureRandom random = new SecureRandom();
    
    public static int generate_random() {
        return random.nextInt();
    }

    public static void tune_tcp_socket(SocketChannel fd) throws SocketException {
        //  Disable Nagle's algorithm. We are doing data batching on 0MQ level,
        //  so using Nagle wouldn't improve throughput in anyway, but it would
        //  hurt latency.
        
        fd.socket().setTcpNoDelay(true);

    }
    
    public static void tune_tcp_keepalives(SocketChannel fd, int tcp_keepalive,
            int tcp_keepalive_cnt, int tcp_keepalive_idle,
            int tcp_keepalive_intvl) throws SocketException {

        Socket s = fd.socket();
        if (tcp_keepalive != -1) {
            s.setKeepAlive(true);
        }
    }

    public static void unblock_socket(SelectableChannel s) throws IOException {
        s.configureBlocking(false);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T[] realloc(Class<T> klass, T[] table, short size, boolean ended) {
        T[] newTable = (T[])(Array.newInstance(klass, size));
        
        if (size > table.length) {
            if (ended)
                System.arraycopy(table, 0, newTable, 0, table.length);
            else
                System.arraycopy(table, 0, newTable, size-table.length, table.length);
        } else if (size < table.length) {
            if (ended)
                System.arraycopy(table, table.length - size, newTable, 0, size);
            else
                System.arraycopy(table, 0, newTable, 0, size);
        }
        return newTable;
    }
    
    public static <T> void swap(List<T> items, int index1_, int index2_) {
        T item1 = items.get(index1_);
        T item2 = items.get(index2_);
        if (item1 != null)
            items.set(index2_, item1);
        if (item2 != null)
            items.set(index1_, item2);
    }

}
