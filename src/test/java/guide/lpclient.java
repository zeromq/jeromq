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
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

//
// Lazy Pirate client
// Use zmq_poll to do a safe request-reply
// To run, start lpserver and then randomly kill/restart it
//

public class lpclient
{

    private final static int REQUEST_TIMEOUT = 2500;    //  msecs, (> 1000!)
    private final static int REQUEST_RETRIES = 3;       //  Before we abandon
    private final static String SERVER_ENDPOINT = "tcp://localhost:5555";

    public static void main(String[] argv)
    {
        ZContext ctx = new ZContext();
        System.out.println("I: connecting to server");
        Socket client = ctx.createSocket(ZMQ.REQ);
        assert (client != null);
        client.connect(SERVER_ENDPOINT);

        int sequence = 0;
        int retriesLeft = REQUEST_RETRIES;
        while (retriesLeft > 0 && !Thread.currentThread().isInterrupted()) {
            //  We send a request, then we work to get a reply
            String request = String.format("%d", ++sequence);
            client.send(request);

            int expect_reply = 1;
            while (expect_reply > 0) {
                //  Poll socket for a reply, with timeout
                PollItem items[] = {new PollItem(client, Poller.POLLIN)};
                int rc = ZMQ.poll(items, REQUEST_TIMEOUT);
                if (rc == -1)
                    break;          //  Interrupted

                //  Here we process a server reply and exit our loop if the
                //  reply is valid. If we didn't a reply we close the client
                //  socket and resend the request. We try a number of times
                //  before finally abandoning:

                if (items[0].isReadable()) {
                    //  We got a reply from the server, must match getSequence
                    String reply = client.recvStr();
                    if (reply == null)
                        break;      //  Interrupted
                    if (Integer.parseInt(reply) == sequence) {
                        System.out.printf("I: server replied OK (%s)\n", reply);
                        retriesLeft = REQUEST_RETRIES;
                        expect_reply = 0;
                    } else
                        System.out.printf("E: malformed reply from server: %s\n",
                                reply);

                } else if (--retriesLeft == 0) {
                    System.out.println("E: server seems to be offline, abandoning\n");
                    break;
                } else {
                    System.out.println("W: no response from server, retrying\n");
                    //  Old socket is confused; close it and open a new one
                    ctx.destroySocket(client);
                    System.out.println("I: reconnecting to server\n");
                    client = ctx.createSocket(ZMQ.REQ);
                    client.connect(SERVER_ENDPOINT);
                    //  Send request again, on new socket
                    client.send(request);
                }
            }
        }
        ctx.destroy();
    }
}
