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
//  Task worker in Java
//  Connects PULL socket to tcp://localhost:5557
//  Collects workloads from ventilator via that socket
//  Connects PUSH socket to tcp://localhost:5558
//  Sends results to sink via that socket
//
public class taskwork {

    public static void main (String[] args) throws Exception {
        ZMQ.Context context = ZMQ.context(1);

        //  Socket to receive messages on
        ZMQ.Socket receiver = context.socket(ZMQ.PULL);
        receiver.connect("tcp://localhost:5557");

        //  Socket to send messages to
        ZMQ.Socket sender = context.socket(ZMQ.PUSH);
        sender.connect("tcp://localhost:5558");

        //  Process tasks forever
        while (!Thread.currentThread ().isInterrupted ()) {
            String string = new String(receiver.recv(0), ZMQ.CHARSET).trim();
            long msec = Long.parseLong(string);
            //  Simple progress indicator for the viewer
            System.out.flush();
            System.out.print(string + '.');

            //  Do the work
            Thread.sleep(msec);

            //  Send results to sink
            sender.send(ZMQ.MESSAGE_SEPARATOR, 0);
        }
        sender.close();
        receiver.close();
        context.term();
    }
}
