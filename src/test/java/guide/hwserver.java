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

//
//  Hello World server in Java
//  Binds REP socket to tcp://*:5555
//  Expects "Hello" from client, replies with "World"
//

import org.zeromq.ZMQ;

public class hwserver{

    public static void main (String[] args) throws Exception{
        ZMQ.Context context = ZMQ.context(1);

        //  Socket to talk to clients
        ZMQ.Socket socket = context.socket(ZMQ.REP);

        socket.bind ("tcp://*:5555");

        while (!Thread.currentThread ().isInterrupted ()) {

            byte[] reply = socket.recv(0);
            System.out.println("Received " + ": [" + new String(reply, ZMQ.CHARSET) + "]");

            //  Create a "Hello" message.
            String request = "world" ;
            // Send the message
            socket.send(request.getBytes (ZMQ.CHARSET), 0);

            Thread.sleep(1000); //  Do some 'work'
        }
        
        socket.close();
        context.term();
    }
}
