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

/**
* Synchronized subscriber.
*/
public class syncsub{

    public static void main (String[] args) {
        Context context = ZMQ.context(1);

        //  First, connect our subscriber socket
        Socket subscriber = context.socket(ZMQ.SUB);
        subscriber.connect("tcp://localhost:5561");
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

        //  Second, synchronize with publisher
        Socket syncclient = context.socket(ZMQ.REQ);
        syncclient.connect("tcp://localhost:5562");

        //  - send a synchronization request
        syncclient.send(ZMQ.MESSAGE_SEPARATOR, 0);

        //  - wait for synchronization reply
        syncclient.recv(0);

        //  Third, get our updates and report how many we got
        int update_nbr = 0;
        while (true) {
            String string = subscriber.recvStr(0);
            if (string.equals("END")) {
                break;
            }
            update_nbr++;
        }
        System.out.println("Received " + update_nbr + " updates.");

        subscriber.close();
        syncclient.close();
        context.term();
    }
}
