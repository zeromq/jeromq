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
* Weather proxy device.
*/
public class wuproxy{

    public static void main (String[] args) {
        //  Prepare our context and sockets
        Context context = ZMQ.context(1);

        //  This is where the weather server sits
        Socket frontend =  context.socket(ZMQ.SUB);
        frontend.connect("tcp://192.168.55.210:5556");

        //  This is our public endpoint for subscribers
        Socket backend  = context.socket(ZMQ.PUB);
        backend.bind("tcp://10.1.1.0:8100");

        //  Subscribe on everything
        frontend.subscribe(ZMQ.SUBSCRIPTION_ALL);

        //  Run the proxy until the user interrupts us
        ZMQ.proxy (frontend, backend, null);

        frontend.close();
        backend.close();
        context.term();
    }
}
