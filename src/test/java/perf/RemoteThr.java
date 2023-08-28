package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class RemoteThr
{
    private RemoteThr()
    {
    }

    public static void main(String[] argv)
    {
        String connectTo;
        long messageCount;
        int messageSize;
        Ctx ctx;
        SocketBase s;
        boolean rc;
        long i;
        Msg msg;

        if (argv.length != 3) {
            printf("usage: remote_thr <connect-to> <message-size> <message-count>\n");
            return;
        }
        connectTo = argv[0];
        messageSize = atoi(argv[1]);
        messageCount = atol(argv[2]);

        ctx = ZMQ.init(1);
        if (ctx == null) {
            printf("error in init");
            return;
        }

        s = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        if (s == null) {
            printf("error in socket");
        }

        //  Add your socket options here.
        //  For example ZMQ_RATE, ZMQ_RECOVERY_IVL and ZMQ_MCAST_LOOP for PGM.

        rc = ZMQ.connect(s, connectTo);
        if (!rc) {
            printf("error in connect: %s\n");
            return;
        }

        for (i = 0; i != messageCount; i++) {
            msg = ZMQ.msgInitWithSize(messageSize);
            if (msg == null) {
                printf("error in msg_init: %s\n");
                return;
            }

            int n = ZMQ.sendMsg(s, msg, 0);
            if (n < 0) {
                printf("error in sendmsg: %s\n");
                return;
            }
        }

        ZMQ.close(s);

        ZMQ.term(ctx);

    }

    private static int atoi(String string)
    {
        return Integer.parseInt(string);
    }

    private static long atol(String string)
    {
        return Long.parseLong(string);
    }

    private static void printf(String string)
    {
        System.out.println(string);
    }
}
