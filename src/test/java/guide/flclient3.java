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

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

//  Freelance client - Model 3
//  Uses flcliapi class to encapsulate Freelance pattern
public class flclient3
{
    public static void main (String[] argv)
    {
        //  Create new freelance client object
        flcliapi client = new flcliapi();

        //  Connect to several endpoints
        client.connect("tcp://localhost:5555");
        client.connect("tcp://localhost:5556");
        client.connect("tcp://localhost:5557");

        //  Send a bunch of name resolution 'requests', measure time
        int requests = 10000;
        long start = System.currentTimeMillis();
        while (requests-- > 0) {
            ZMsg request = new ZMsg();
            request.add("random name");
            ZMsg reply = client.request(request);
            if (reply == null) {
                System.out.printf("E: name service not available, aborting\n");
                break;
            }
            reply.destroy();
        }
        System.out.printf("Average round trip cost: %d usec\n",
                (int) (System.currentTimeMillis() - start) / 10);

        client.destroy();
    }

}
