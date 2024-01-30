package guide;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class ZHelper
{
    private static final Random rand = new Random(System.currentTimeMillis());

    /**
     * Receives all message parts from socket, prints neatly
     * @param sock
     */
    public static void dump(Socket sock)
    {
        System.out.println("----------------------------------------");
        while (true) {
            byte[] msg = sock.recv(0);
            boolean isText = true;
            StringBuilder data = new StringBuilder();
            for (byte b : msg) {
                if (b < 32 || b > 127)
                    isText = false;
                data.append(String.format("%02X", b));
            }
            if (isText)
                data = new StringBuilder(new String(msg, ZMQ.CHARSET));

            System.out.printf("[%03d] %s%n", msg.length, data);
            if (!sock.hasReceiveMore())
                break;
        }
    }

    public static void setId(Socket sock)
    {
        String identity = String.format("%04X-%04X", rand.nextInt(), rand.nextInt());

        sock.setIdentity(identity.getBytes(ZMQ.CHARSET));
    }

    public static List<Socket> buildZPipe(Context ctx)
    {
        Socket socket1 = ctx.socket(SocketType.PAIR);
        socket1.setLinger(0);
        socket1.setHWM(1);

        Socket socket2 = ctx.socket(SocketType.PAIR);
        socket2.setLinger(0);
        socket2.setHWM(1);

        String iface = "inproc://" + new BigInteger(130, rand).toString(32);
        socket1.bind(iface);
        socket2.connect(iface);

        return Arrays.asList(socket1, socket2);
    }
}
