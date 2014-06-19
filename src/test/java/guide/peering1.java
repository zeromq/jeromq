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

import java.util.Random;

import org.zeromq.ZMQ.Poller;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;

//  Broker peering simulation (part 1)
//  Prototypes the state flow

public class peering1
{

    public static void main(String[] argv)
    {
        //  First argument is this broker's name
        //  Other arguments are our peers' names
        //
        if (argv.length < 1) {
            System.out.println("syntax: peering1 me {you}\n");
            System.exit(-1);
        }
        String self = argv[0];
        System.out.println(String.format("I: preparing broker at %s\n", self));
        Random rand = new Random(System.nanoTime());

        ZContext ctx = new ZContext();

        //  Bind state backend to endpoint
        Socket statebe = ctx.createSocket(ZMQ.PUB);
        statebe.bind(String.format("ipc://%s-state.ipc", self));

        //  Connect statefe to all peers
        Socket statefe = ctx.createSocket(ZMQ.SUB);
        statefe.subscribe(ZMQ.SUBSCRIPTION_ALL);
        int argn;
        for (argn = 1; argn < argv.length; argn++) {
            String peer = argv[argn];
            System.out.printf("I: connecting to state backend at '%s'\n", peer);
            statefe.connect(String.format("ipc://%s-state.ipc", peer));
        }
        //  The main loop sends out status messages to peers, and collects
        //  status messages back from peers. The zmq_poll timeout defines
        //  our own heartbeat:

        while (true) {
            //  Poll for activity, or 1 second timeout
            PollItem items[] = {new PollItem(statefe, Poller.POLLIN)};
            int rc = ZMQ.poll(items, 1000);
            if (rc == -1)
                break;              //  Interrupted

            //  Handle incoming status messages
            if (items[0].isReadable()) {
                String peer_name = new String(statefe.recv(0), ZMQ.CHARSET);
                String available = new String(statefe.recv(0), ZMQ.CHARSET);
                System.out.printf("%s - %s workers free\n", peer_name, available);
            } else {
                //  Send random values for worker availability
                statebe.send(self, ZMQ.SNDMORE);
                statebe.send(String.format("%d", rand.nextInt(10)), 0);
            }
        }
        ctx.destroy();
    }
}
