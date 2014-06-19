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

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

//  Hello World worker
//  Connects REP socket to tcp://*:5560
//  Expects "Hello" from client, replies with "World"
public class rrworker
{
    public static void main (String[] args) throws Exception {
        Context context = ZMQ.context (1);

        //  Socket to talk to server
        Socket responder = context.socket (ZMQ.REP);
        responder.connect ("tcp://localhost:5560");

        while (!Thread.currentThread ().isInterrupted ()) {
            //  Wait for next request from client
            String string = responder.recvStr (0);
            System.out.printf ("Received request: [%s]\n", string);

            //  Do some 'work'
            Thread.sleep (1000);

            //  Send reply back to client
            responder.send ("World");
        }

        //  We never get here but clean up anyhow
        responder.close();
        context.term();
    }
}
