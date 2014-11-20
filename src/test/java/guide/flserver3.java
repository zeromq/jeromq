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
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

//  Freelance server - Model 3
//  Uses an ROUTER/ROUTER socket but just one thread
public class flserver3
{
    public static void main(String[] args)
    {
        boolean verbose = (args.length > 0 && args[0].equals("-v"));

        ZContext ctx = new ZContext();
        //  Prepare server socket with predictable identity
        String bindEndpoint = "tcp://*:5555";
        String connectEndpoint = "tcp://localhost:5555";
        Socket server = ctx.createSocket(ZMQ.ROUTER);
        server.setIdentity(connectEndpoint.getBytes(ZMQ.CHARSET));
        server.bind(bindEndpoint);
        System.out.printf ("I: service is ready at %s\n", bindEndpoint);

        while (!Thread.currentThread().isInterrupted()) {
            ZMsg request = ZMsg.recvMsg(server);
            if (verbose && request != null)
                request.dump(System.out);

            if (request == null)
                break;          //  Interrupted

            //  Frame 0: identity of client
            //  Frame 1: PING, or client control frame
            //  Frame 2: request body
            ZFrame identity = request.pop();
            ZFrame control = request.pop();
            ZMsg reply = new ZMsg();
            if (control.equals(new ZFrame("PING")))
                reply.add("PONG");
            else {
                reply.add(control);
                reply.add("OK");
            }
            request.destroy();
            reply.push(identity);
            if (verbose && reply != null)
                reply.dump(System.out);
            reply.send(server);
        }
        if (Thread.currentThread().isInterrupted())
            System.out.printf ("W: interrupted\n");

        ctx.destroy();
    }
}
