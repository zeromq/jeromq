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

//  Binary Star client proof-of-concept implementation. This client does no
//  real work; it just demonstrates the Binary Star failover model.
public class bstarcli
{
    private static final long REQUEST_TIMEOUT = 1000;    //  msecs
    private static final long SETTLE_DELAY = 2000;       //  Before failing over

    public static void main(String[] argv) throws Exception
    {
        ZContext ctx = new ZContext();

        String[] server = { "tcp://localhost:5001", "tcp://localhost:5002" };
        int serverNbr = 0;

        System.out.printf ("I: connecting to server at %s...\n", server [serverNbr]);
        Socket client = ctx.createSocket(ZMQ.REQ);
        client.connect(server[serverNbr]);

        int sequence = 0;
        while (!Thread.currentThread().isInterrupted()) {
            //  We send a request, then we work to get a reply
            String request = String.format("%d", ++sequence);
            client.send(request);

            boolean expectReply = true;
            while (expectReply) {
                //  Poll socket for a reply, with timeout
                PollItem items [] = { new PollItem(client, ZMQ.Poller.POLLIN) };
                int rc = ZMQ.poll(items, 1, REQUEST_TIMEOUT);
                if (rc == -1)
                    break;          //  Interrupted

                //  .split main body of client
                //  We use a Lazy Pirate strategy in the client. If there's no
                //  reply within our timeout, we close the socket and try again.
                //  In Binary Star, it's the client vote that decides which
                //  server is primary; the client must therefore try to connect
                //  to each server in turn:

                if (items[0].isReadable()) {
                    //  We got a reply from the server, must match getSequence
                    String reply = client.recvStr();
                    if (Integer.parseInt(reply) == sequence) {
                        System.out.printf ("I: server replied OK (%s)\n", reply);
                        expectReply = false;
                        Thread.sleep(1000);  //  One request per second
                    }
                    else
                        System.out.printf ("E: bad reply from server: %s\n", reply);
                }
                else {
                    System.out.printf ("W: no response from server, failing over\n");

                    //  Old socket is confused; close it and open a new one
                    ctx.destroySocket(client);
                    serverNbr = (serverNbr + 1) % 2;
                    Thread.sleep(SETTLE_DELAY);
                    System.out.printf("I: connecting to server at %s...\n",
                            server[serverNbr]);
                    client = ctx.createSocket(ZMQ.REQ);
                    client.connect(server[serverNbr]);

                    //  Send request again, on new socket
                    client.send(request);
                }
            }
        }
        ctx.destroy();

    }
}
