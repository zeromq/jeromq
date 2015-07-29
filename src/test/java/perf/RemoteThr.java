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

public class RemoteThr
{
    private RemoteThr()
    {
    }

    public static void main(String[] argv)
    {
        String connectTo;
        long messageCount;
        int  messageSize;
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
