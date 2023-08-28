package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class LocalLat
{
    private LocalLat()
    {
    }

    public static void main(String[] args)
    {
        String bindTo;
        int roundtripCount;
        int messageSize;
        Ctx ctx;
        SocketBase s;
        boolean rc;
        int n;
        int i;
        Msg msg;

        if (args.length != 3) {
            printf("usage: local_lat <bind-to> <message-size> " + "<roundtrip-count>\n");
            return;
        }
        bindTo = args[0];
        messageSize = atoi(args[1]);
        roundtripCount = atoi(args[2]);

        ctx = ZMQ.init(1);
        if (ctx == null) {
            printf("error in init: %s\n");
            return;
        }

        s = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        if (s == null) {
            printf("error in socket: %s\n", ZMQ.strerror(ctx.errno().get()));
            return;
        }

        rc = ZMQ.bind(s, bindTo);
        if (!rc) {
            printf("error in bind: %s\n", ZMQ.strerror(s.errno()));
            return;
        }

        for (i = 0; i != roundtripCount; i++) {
            msg = ZMQ.recvMsg(s, 0);
            if (msg == null) {
                printf("error in recvmsg: %s\n", ZMQ.strerror(s.errno()));
                return;
            }
            if (ZMQ.msgSize(msg) != messageSize) {
                printf("message of incorrect size received\n");
                return;
            }
            n = ZMQ.sendMsg(s, msg, 0);
            if (n < 0) {
                printf("error in sendmsg: %s\n", ZMQ.strerror(s.errno()));
                return;
            }
        }

        ZMQ.sleep(1000);

        ZMQ.close(s);

        ZMQ.term(ctx);
    }

    private static int atoi(String string)
    {
        return Integer.parseInt(string);
    }

    private static void printf(String string)
    {
        System.out.println(string);
    }

    private static void printf(String string, Object... args)
    {
        System.out.printf((string) + "%n", args);
    }
}
