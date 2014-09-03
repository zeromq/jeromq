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
            printf("usage: local_lat <bind-to> <message-size> "
               + "<roundtrip-count>\n");
            return;
        }
        bindTo = args [0];
        messageSize = atoi(args [1]);
        roundtripCount = atoi(args [2]);

        ctx = ZMQ.zmqInit(1);
        if (ctx == null) {
            printf("error in zmqInit: %s\n");
            return;
        }

        s = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_REP);
        if (s == null) {
            printf("error in zmq_socket: %s\n", ZMQ.zmq_strerror(s.errno()));
            return;
        }

        rc = ZMQ.zmq_bind(s, bindTo);
        if (!rc) {
            printf("error in zmq_bind: %s\n", ZMQ.zmq_strerror(s.errno()));
            return;
        }

        for (i = 0; i != roundtripCount; i++) {
            msg = ZMQ.zmq_recvmsg(s, 0);
            if (msg == null) {
                printf("error in zmq_recvmsg: %s\n", ZMQ.zmq_strerror(s.errno()));
                return;
            }
            if (ZMQ.zmq_msg_size(msg) != messageSize) {
                printf("message of incorrect size received\n");
                return;
            }
            n = ZMQ.zmq_sendmsg(s, msg, 0);
            if (n < 0) {
                printf("error in zmq_sendmsg: %s\n", ZMQ.zmq_strerror(s.errno()));
                return;
            }
        }

        ZMQ.zmq_sleep(1);

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
