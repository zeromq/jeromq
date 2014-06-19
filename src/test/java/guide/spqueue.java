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

import java.util.ArrayList;

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

//
// Simple Pirate queue
// This is identical to load-balancing  pattern, with no reliability mechanisms
// at all. It depends on the client for recovery. Runs forever.
//
public class spqueue
{

    private final static String WORKER_READY = "\001";      //  Signals worker is ready

    public static void main(String[] args)
    {
        ZContext ctx = new ZContext();
        Socket frontend = ctx.createSocket(ZMQ.ROUTER);
        Socket backend = ctx.createSocket(ZMQ.ROUTER);
        frontend.bind("tcp://*:5555");    //  For clients
        backend.bind("tcp://*:5556");    //  For workers

        //  Queue of available workers
        ArrayList<ZFrame> workers = new ArrayList<ZFrame>();

        //  The body of this example is exactly the same as lruqueue2.
        while (true) {
            PollItem items[] = {
                    new PollItem(backend, Poller.POLLIN),
                    new PollItem(frontend, Poller.POLLIN)
            };
            int rc = ZMQ.poll(items, workers.size() > 0 ? 2 : 1, -1);

            //  Poll frontend only if we have available workers
            if (rc == -1)
                break;              //  Interrupted

            //  Handle worker activity on backend
            if (items[0].isReadable()) {
                //  Use worker address for LRU routing
                ZMsg msg = ZMsg.recvMsg(backend);
                if (msg == null)
                    break;          //  Interrupted
                ZFrame address = msg.unwrap();
                workers.add(address);

                //  Forward message to client if it's not a READY
                ZFrame frame = msg.getFirst();
                if (new String(frame.getData(), ZMQ.CHARSET).equals(WORKER_READY))
                    msg.destroy();
                else
                    msg.send(frontend);
            }
            if (items[1].isReadable()) {
                //  Get client request, route to first available worker
                ZMsg msg = ZMsg.recvMsg(frontend);
                if (msg != null) {
                    msg.wrap(workers.remove(0));
                    msg.send(backend);
                }
            }
        }
        //  When we're done, clean up properly
        while (workers.size() > 0) {
            ZFrame frame = workers.remove(0);
            frame.destroy();
        }
        workers.clear();
        ctx.destroy();
    }

}
