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

/**
 *  Task worker - design 2
 *  Adds pub-sub flow to receive and respond to kill signal
 */
public class taskwork2 {

    public static void main (String[] args) throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket receiver = context.socket(ZMQ.PULL);
        receiver.connect("tcp://localhost:5557");

        ZMQ.Socket sender = context.socket(ZMQ.PUSH);
        sender.connect("tcp://localhost:5558");

        ZMQ.Socket controller = context.socket(ZMQ.SUB);
        controller.connect("tcp://localhost:5559");
        controller.subscribe(ZMQ.SUBSCRIPTION_ALL);

        ZMQ.Poller items = new ZMQ.Poller (2);
        items.register(receiver, ZMQ.Poller.POLLIN);
        items.register(controller, ZMQ.Poller.POLLIN);

        while (true) {
            
            items.poll();
            
            if (items.pollin(0)) {

                String message = receiver.recvStr (0);
                long nsec = Long.parseLong(message);
                
                //  Simple progress indicator for the viewer
                System.out.print(message + '.');
                System.out.flush();

                //  Do the work
                Thread.sleep(nsec);

                //  Send results to sink
                sender.send("", 0);
            }
            //  Any waiting controller command acts as 'KILL'
            if (items.pollin(1)) {
                break; // Exit loop
            }

        }
        
        // Finished
        receiver.close();
        sender.close();
        controller.close();
        context.term();
    }
}
