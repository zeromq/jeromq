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
//Custom routing Router to Papa (ROUTER to REP)
//

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class rtpapa
{

    //  We will do this all in one thread to emphasize the getSequence
    //  of events
    public static void main(String[] args)
    {
        Context context = ZMQ.context(1);

        Socket client = context.socket(ZMQ.ROUTER);
        client.bind("ipc://routing.ipc");

        Socket worker = context.socket(ZMQ.REP);
        worker.setIdentity("A".getBytes(ZMQ.CHARSET));
        worker.connect("ipc://routing.ipc");

        //  Wait for the worker to connect so that when we send a message
        //  with routing envelope, it will actually match the worker
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //  Send papa address, address stack, empty part, and request
        client.send("A", ZMQ.SNDMORE);
        client.send("address 3", ZMQ.SNDMORE);
        client.send("address 2", ZMQ.SNDMORE);
        client.send("address 1", ZMQ.SNDMORE);
        client.send("", ZMQ.SNDMORE);
        client.send("This is the workload", 0);

        //  Worker should get just the workload
        ZHelper.dump(worker);

        //  We don't play with envelopes in the worker
        worker.send("This is the reply", 0);

        //  Now dump what we got off the ROUTER socket
        ZHelper.dump(client);

        client.close();
        worker.close();
        context.term();

    }

}
