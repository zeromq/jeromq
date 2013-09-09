package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;

public class LocalLat {

    public static void main(String[] args) {
        String bind_to;
        int roundtrip_count;
        int message_size;
        Ctx ctx;
        SocketBase s;
        boolean rc;
        int n;
        int i;
        Msg msg;

        if (args.length != 3) {
            printf ("usage: local_lat <bind-to> <message-size> "
               + "<roundtrip-count>\n");
            return ;
        }
        bind_to = args [0];
        message_size = atoi (args [1]);
        roundtrip_count = atoi (args [2]);

        ctx = ZMQ.zmq_init (1);
        if (ctx == null) {
            printf ("error in zmq_init: %s\n");
            return ;
        }

        s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REP);
        if (s == null) {
            printf ("error in zmq_socket: %s\n", ZMQ.zmq_strerror (s.errno()));
            return ;
        }

        rc = ZMQ.zmq_bind (s, bind_to);
        if (!rc) {
            printf ("error in zmq_bind: %s\n", ZMQ.zmq_strerror (s.errno()));
            return;
        }

        for (i = 0; i != roundtrip_count; i++) {
            msg = ZMQ.zmq_recvmsg (s, 0);
            if (msg == null) {
                printf ("error in zmq_recvmsg: %s\n", ZMQ.zmq_strerror (s.errno()));
                return ;
            }
            if (ZMQ.zmq_msg_size (msg) != message_size) {
                printf ("message of incorrect size received\n");
                return ;
            }
            n = ZMQ.zmq_sendmsg (s, msg, 0);
            if (n < 0) {
                printf ("error in zmq_sendmsg: %s\n", ZMQ.zmq_strerror (s.errno()));
                return;
            }
        }


        ZMQ.zmq_sleep (1);

        ZMQ.zmq_close (s);

        ZMQ.zmq_term (ctx);

    }

    private static int atoi(String string) {
        return Integer.parseInt(string);
    }

    private static void printf(String string) {
        System.out.println(string);
    }
    
    private static void printf(String string, Object ... args) {
        System.out.println(String.format(string, args));
    }        
}
