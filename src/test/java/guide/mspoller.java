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
//  This version uses ZMQ.Poller
//
public class mspoller {

    public static void main (String[] args) {
        ZMQ.Context context = ZMQ.context(1);

        // Connect to task ventilator
        ZMQ.Socket receiver = context.socket(ZMQ.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Connect to weather server
        ZMQ.Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5556");
        subscriber.subscribe("10001 ".getBytes(ZMQ.CHARSET));

        //  Initialize poll set
        ZMQ.Poller items = new ZMQ.Poller (2);
        items.register(receiver, ZMQ.Poller.POLLIN);
        items.register(subscriber, ZMQ.Poller.POLLIN);

        //  Process messages from both sockets
        while (!Thread.currentThread ().isInterrupted ()) {
            byte[] message;
            items.poll();
            if (items.pollin(0)) {
                message = receiver.recv(0);
                System.out.println("Process task");
            }
            if (items.pollin(1)) {
                message = subscriber.recv(0);
                System.out.println("Process weather update");
            }
        }
        receiver.close ();
        context.term ();
    }
}
