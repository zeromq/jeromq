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

package perf;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class LocalThr
{
    private LocalThr()
    {
    }

    public static void main(String[] argv)
    {
        String bindTo;
        long messageCount;
        int  messageSize;
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
            printf("usage: local_thr <bind-to> <message-size> <message-count>\n");
            return;
        }
        bindTo = argv [0];
        messageSize = atoi(argv [1]);
        messageCount = atol(argv [2]);

        ctx = ZMQ.init(1);
        if (ctx == null) {
            printf("error in init");
            return;
        }

        s = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        if (s == null) {
            printf("error in socket");
        }

        //  Add your socket options here.
        //  For example ZMQ_RATE, ZMQ_RECOVERY_IVL and ZMQ_MCAST_LOOP for PGM.

        rc = ZMQ.bind(s, bindTo);
        if (!rc) {
            printf("error in bind: %s\n");
            return;
        }

        msg = ZMQ.recvMsg(s, 0);
        if (msg == null) {
            printf("error in recvmsg: %s\n");
            return;
        }

        watch = ZMQ.startStopwatch();

        for (i = 0; i != messageCount - 1; i++) {
            msg = ZMQ.recvMsg(s, 0);
            if (msg == null) {
                printf("error in recvmsg: %s\n");
                return;
            }
            if (ZMQ.msgSize(msg) != messageSize) {
                printf("message of incorrect size received " + ZMQ.msgSize(msg));
                return;
            }
        }

        elapsed = ZMQ.stopStopwatch(watch);
        if (elapsed == 0) {
            elapsed = 1;
        }

        throughput = (long)
                ((double) messageCount / (double) elapsed * 1000000L);
        megabits = (double) (throughput * messageSize * 8) / 1000000;

        printf("message elapsed: %.3f \n", (double) elapsed / 1000000L);
        printf("message size: %d [B]\n", (int) messageSize);
        printf("message count: %d\n", (int) messageCount);
        printf("mean throughput: %d [msg/s]\n", (int) throughput);
        printf("mean throughput: %.3f [Mb/s]\n", (double) megabits);

        ZMQ.close(s);

        ZMQ.term(ctx);
    }

    private static void printf(String str, Object ... args)
    {
        // TODO Auto-generated method stub
        System.out.println(String.format(str, args));
    }

    private static int atoi(String string)
    {
        return Integer.valueOf(string);
    }

    private static long atol(String string)
    {
        return Long.valueOf(string);
    }

    private static void printf(String string)
    {
        System.out.println(string);
    }
}
