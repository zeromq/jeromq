/*
    Copyright (c) 2007-2012 iMatix Corporation
    Copyright (c) 2009-2011 250bpm s.r.o.
    Copyright (c) 2007-2012 Other contributors as noted in the AUTHORS file

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
package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class LocalThr {

    public static void main(String[] argv) {
        String bind_to;
        long message_count;
        int  message_size;
        Ctx ctx;
        SocketBase s;
        boolean rc;
        long i;
        Msg msg;
        long watch;
        long elapsed;
        long throughput;
        double megabits;

        if (argv.length != 3) {
            printf ("usage: local_thr <bind-to> <message-size> <message-count>\n");
            return ;
        }
        bind_to = argv [0];
        message_size = atoi (argv [1]);
        message_count = atol (argv [2]);

        ctx = ZMQ.zmq_init (1);
        if (ctx == null) {
            printf ("error in zmq_init");
            return ;
        }

        s = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PULL);
        if (s== null) {
            printf ("error in zmq_socket");
        }

        //  Add your socket options here.
        //  For example ZMQ_RATE, ZMQ_RECOVERY_IVL and ZMQ_MCAST_LOOP for PGM.

        rc = ZMQ.zmq_bind (s, bind_to);
        if (!rc) {
            printf ("error in zmq_bind: %s\n");
            return;
        }
        /*
        msg = ZMQ.zmq_msg_init ();
        if (msg == null ) {
            printf ("error in zmq_msg_init: %s\n");
            return;
        }
        */

        msg = ZMQ.zmq_recvmsg (s, 0);
        if (msg == null) {
            printf ("error in zmq_recvmsg: %s\n");
            return;
        }
        /*
        if (ZMQ.zmq_msg_size (msg) != message_size) {
            printf ("message of incorrect size received\n");
            return;
        }
        */
        
        watch = ZMQ.zmq_stopwatch_start ();

        for (i = 0; i != message_count-1 ; i++) {
            msg = ZMQ.zmq_recvmsg (s, 0);
            if (msg == null) {
                printf ("error in zmq_recvmsg: %s\n");
                return ;
            }
            if (ZMQ.zmq_msg_size (msg) != message_size) {
                printf ("message of incorrect size received " + ZMQ.zmq_msg_size(msg));
                return;
            }
        }

        elapsed = ZMQ.zmq_stopwatch_stop (watch);
        if (elapsed == 0)
            elapsed = 1;

        throughput = ( long)
                ((double) message_count / (double) elapsed * 1000000L);
        megabits = (double) (throughput * message_size * 8) / 1000000;

        printf ("message elapsed: %.3f \n", (double) elapsed / 1000000L);
        printf ("message size: %d [B]\n", (int) message_size);
        printf ("message count: %d\n", (int) message_count);
        printf ("mean throughput: %d [msg/s]\n", (int) throughput);
        printf ("mean throughput: %.3f [Mb/s]\n", (double) megabits);

        ZMQ.zmq_close (s);

        ZMQ.zmq_term (ctx);


    }


    private static void printf(String str, Object ... args) {
        // TODO Auto-generated method stub
        System.out.println(String.format(str, args));
        
    }


    private static int atoi(String string) {
        return Integer.valueOf(string);
    }
    
    private static long atol(String string) {
        return Long.valueOf(string);
    }

    private static void printf(String string) {
        System.out.println(string);
    }

}
