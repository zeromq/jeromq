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

//
//  Reading from multiple sockets in Java
//  This version uses a simple recv loop
//
public class msreader {

    public static void main (String[] args) throws Exception {
        //  Prepare our context and sockets
        ZMQ.Context context = ZMQ.context(1);

        // Connect to task ventilator
        ZMQ.Socket receiver = context.socket(ZMQ.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Connect to weather server
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("10001 ".getBytes(ZMQ.CHARSET));

        //  Process messages from both sockets
        //  We prioritize traffic from the task ventilator
        while (!Thread.currentThread ().isInterrupted ()) {
            //  Process any waiting tasks
            byte[] task;
            while((task = receiver.recv(ZMQ.DONTWAIT)) != null) {
                System.out.println("process task");
            }
            //  Process any waiting weather updates
            byte[] update;
            while ((update = subscriber.recv(ZMQ.DONTWAIT)) != null) {
                System.out.println("process weather update");
            }
            //  No activity, so sleep for 1 msec
            Thread.sleep(1000);
        }
        subscriber.close ();
        context.term ();
    }
}
