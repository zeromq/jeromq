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
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
* Simple request-reply broker
*
*/
public class rrbroker{

    public static void main (String[] args) {
        //  Prepare our context and sockets
        Context context = ZMQ.context(1);

        Socket frontend = context.socket(ZMQ.ROUTER);
        Socket backend  = context.socket(ZMQ.DEALER);
        frontend.bind("tcp://*:5559");
        backend.bind("tcp://*:5560");

        System.out.println("launch and connect broker.");

        //  Initialize poll set
        Poller items = new Poller (2);
        items.register(frontend, Poller.POLLIN);
        items.register(backend, Poller.POLLIN);

        boolean more = false;
        byte[] message;

        //  Switch messages between sockets
        while (!Thread.currentThread().isInterrupted()) {            
            //  poll and memorize multipart detection
            items.poll();

            if (items.pollin(0)) {
                while (true) {
                    // receive message
                    message = frontend.recv(0);
                    more = frontend.hasReceiveMore();

                    // Broker it
                    backend.send(message, more ? ZMQ.SNDMORE : 0);
                    if(!more){
                        break;
                    }
                }
            }
            if (items.pollin(1)) {
                while (true) {
                    // receive message
                    message = backend.recv(0);
                    more = backend.hasReceiveMore();
                    // Broker it
                    frontend.send(message,  more ? ZMQ.SNDMORE : 0);
                    if(!more){
                        break;
                    }
                }
            }
        }
        //  We never get here but clean up anyhow
        frontend.close();
        backend.close();
        context.term();
    }
}
