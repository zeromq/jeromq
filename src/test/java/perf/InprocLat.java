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

public class InprocLat
{
    private InprocLat()
    {
    }

    static class Worker implements Runnable
    {
        private Ctx ctx;
        private int roundtripCount;
        Worker(Ctx ctx, int roundtripCount)
        {
            this.ctx = ctx;
            this.roundtripCount = roundtripCount;
        }

        @Override
        public void run()
        {
            SocketBase s = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REP);
            if (s == null) {
                printf("error in zmq_socket: %s\n");
                exit(1);
            }

            boolean rc = ZMQ.zmq_connect(s, "inproc://lat_test");
            if (!rc) {
                printf("error in zmq_connect: %s\n");
                exit(1);
            }

            Msg msg;

            for (int i = 0; i != roundtripCount; i++) {
                msg = ZMQ.zmq_recvmsg(s, 0);
                if (msg == null) {
                    printf("error in zmq_recvmsg: %s\n");
                    exit(1);
                }
                int r = ZMQ.zmq_sendmsg(s, msg, 0);
                if (r < 0) {
                    printf("error in zmq_sendmsg: %s\n");
                    exit(1);
                }
            }

            ZMQ.zmq_close(s);
        }

        private void exit(int i)
        {
            // TODO Auto-generated method stub
        }
    }

    public static void main(String[] argv) throws Exception
    {
        if (argv.length != 2) {
            printf("usage: inproc_lat <message-size> <roundtrip-count>\n");
            return;
        }

        int messageSize = atoi(argv [0]);
        int roundtripCount = atoi(argv [1]);

        Ctx ctx = ZMQ.zmqInit(1);
        if (ctx == null) {
            printf("error in zmqInit:");
            return;
        }

        SocketBase s = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REQ);
        if (s == null) {
            printf("error in zmq_socket: ");
            return;
        }

        boolean rc = ZMQ.zmq_bind(s, "inproc://lat_test");
        if (!rc) {
            printf("error in zmq_bind: ");
            return;
        }

        Thread localThread = new Thread(new Worker(ctx, roundtripCount));
        localThread.start();

        Msg smsg = ZMQ.zmq_msg_init_size(messageSize);

        printf("message size: %d [B]\n", (int) messageSize);
        printf("roundtrip count: %d\n", (int) roundtripCount);

        long watch = ZMQ.zmq_stopwatch_start();

        for (int i = 0; i != roundtripCount; i++) {
            int r = ZMQ.zmq_sendmsg(s, smsg, 0);
            if (r < 0) {
                printf("error in zmq_sendmsg: %s\n");
                return;
            }
            Msg msg = ZMQ.zmq_recvmsg(s, 0);
            if (msg == null) {
                printf("error in zmq_recvmsg: %s\n");
                return;
            }
            if (ZMQ.zmq_msg_size(msg) != messageSize) {
                printf("message of incorrect size received\n");
                return;
            }
        }

        long elapsed = ZMQ.zmq_stopwatch_stop(watch);

        double latency = (double) elapsed / (roundtripCount * 2);

        localThread.join();

        printf("average latency: %.3f [us]\n", (double) latency);

        ZMQ.zmq_close(s);

        ZMQ.zmq_term(ctx);
    }

    private static int atoi(String string)
    {
        return Integer.parseInt(string);
    }

    private static void printf(String string)
    {
        System.out.println(string);
    }

    private static void printf(String string, Object ... args)
    {
        System.out.println(String.format(string, args));
    }
}
