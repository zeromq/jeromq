package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class InprocLat {

    static class Worker implements Runnable {
        
        private Ctx ctx_;
        private int roundtrip_count;
        Worker(Ctx ctx, int roundtrip_count) {
            this.ctx_ = ctx;
            this.roundtrip_count = roundtrip_count;
        }
        
        @Override
        public void run() {
            SocketBase s = ZMQ.zmq_socket (ctx_, ZMQ.ZMQ_REP);
            if (s == null) {
                printf ("error in zmq_socket: %s\n");
                exit (1);
            }
    
            boolean rc = ZMQ.zmq_connect (s, "inproc://lat_test");
            if (!rc) {
                printf ("error in zmq_connect: %s\n");
                exit (1);
            }
    
            Msg msg ;
    
            for (int i = 0; i != roundtrip_count; i++) {
                msg = ZMQ.zmq_recvmsg (s, 0);
                if (msg == null) {
                    printf ("error in zmq_recvmsg: %s\n");
                    exit (1);
                }
                int r = ZMQ.zmq_sendmsg (s, msg, 0);
                if (r < 0) {
                    printf ("error in zmq_sendmsg: %s\n");
                    exit (1);
                }
            }
    
            ZMQ.zmq_close (s);
        }

        private void exit(int i) {
            // TODO Auto-generated method stub
            
        }
    }
    
    public static void main(String[] argv) throws Exception {
        if (argv.length != 2) {
            printf ("usage: inproc_lat <message-size> <roundtrip-count>\n");
            return;
        }

        int message_size = atoi (argv [0]);
        int roundtrip_count = atoi (argv [1]);

        Ctx ctx = ZMQ.zmq_init (1);
        if (ctx == null) {
            printf ("error in zmq_init:");
            return;
        }

        SocketBase s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        if (s == null) {
            printf ("error in zmq_socket: ");
            return;
        }

        boolean rc = ZMQ.zmq_bind (s, "inproc://lat_test");
        if (!rc) {
            printf ("error in zmq_bind: ");
            return;
        }

        Thread local_thread = new Thread(new Worker(ctx, roundtrip_count));
        local_thread.start();

        Msg smsg = ZMQ.zmq_msg_init_size ( message_size);

        printf ("message size: %d [B]\n", (int) message_size);
        printf ("roundtrip count: %d\n", (int) roundtrip_count);

        long watch = ZMQ.zmq_stopwatch_start ();

        for (int i = 0; i != roundtrip_count; i++) {
            int r = ZMQ.zmq_sendmsg (s, smsg, 0);
            if (r < 0) {
                printf ("error in zmq_sendmsg: %s\n");
                return;
            }
            Msg msg = ZMQ.zmq_recvmsg (s, 0);
            if (msg == null) {
                printf ("error in zmq_recvmsg: %s\n");
                return ;
            }
            if (ZMQ.zmq_msg_size (msg) != message_size) {
                printf ("message of incorrect size received\n");
                return ;
            }
        }

        long elapsed = ZMQ.zmq_stopwatch_stop (watch);

        double latency = (double) elapsed / (roundtrip_count * 2);

        local_thread.join();

        printf ("average latency: %.3f [us]\n", (double) latency);

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
