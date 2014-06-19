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

package guide;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

//  Freelance server - Model 1
//  Trivial echo service
public class flserver1
{
    public static void main(String[] args)
    {
        if (args.length < 1) {
            System.out.printf("I: syntax: flserver1 <endpoint>\n");
            System.exit(0);
        }
        ZContext ctx = new ZContext();
        Socket server = ctx.createSocket(ZMQ.REP);
        server.bind(args[0]);

        System.out.printf ("I: echo service is ready at %s\n", args[0]);
        while (true) {
            ZMsg msg = ZMsg.recvMsg(server);
            if (msg == null)
                break;          //  Interrupted
            msg.send(server);
        }
        if (Thread.currentThread().isInterrupted())
            System.out.printf ("W: interrupted\n");

        ctx.destroy();
    }
}
