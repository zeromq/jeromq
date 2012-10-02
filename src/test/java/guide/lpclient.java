package guide;

import org.jeromq.ZContext;
import org.jeromq.ZMQ;
import org.jeromq.ZMQ.PollItem;
import org.jeromq.ZMQ.Socket;

//
// Lazy Pirate client
// Use zmq_poll to do a safe request-reply
// To run, start lpserver and then randomly kill/restart it
//

public class lpclient {

    private final static int REQUEST_TIMEOUT  =   2500;    //  msecs, (> 1000!)
    private final static int REQUEST_RETRIES  =   3;       //  Before we abandon
    private final static String SERVER_ENDPOINT  =  "tcp://localhost:5555";

    public static void main (String[] argv)
    {
        ZContext ctx = new ZContext ();
        System.out.println ("I: connecting to server…\n");
        Socket client = ctx.createSocket(ZMQ.REQ);
        assert (client != null);
        client.connect(SERVER_ENDPOINT);
    
        int sequence = 0;
        int retries_left = REQUEST_RETRIES;
        while (retries_left > 0 && !Thread.currentThread().isInterrupted()) {
            //  We send a request, then we work to get a reply
            String request = String.format("%d", ++sequence);
            client.send(request);
    
            int expect_reply = 1;
            while (expect_reply > 0) {
                //  Poll socket for a reply, with timeout
                PollItem items [] = { new PollItem( client,  ZMQ.POLLIN) };
                int rc = ZMQ.poll (items, REQUEST_TIMEOUT );
                if (rc == -1)
                    break;          //  Interrupted
    
                //  Here we process a server reply and exit our loop if the
                //  reply is valid. If we didn't a reply we close the client
                //  socket and resend the request. We try a number of times
                //  before finally abandoning:
                
                if (items [0].isReadable()) {
                    //  We got a reply from the server, must match sequence
                    String reply = client.recvStr();
                    if (reply == null)
                        break;      //  Interrupted
                    if (Integer.parseInt (reply) == sequence) {
                        System.out.println (String.format("I: server replied OK (%s)\n", reply));
                        retries_left = REQUEST_RETRIES;
                        expect_reply = 0;
                    }
                    else
                        System.out.println (String.format("E: malformed reply from server: %s\n",
                            reply));
    
                }
                else
                if (--retries_left == 0) {
                    System.out.println ("E: server seems to be offline, abandoning\n");
                    break;
                }
                else {
                    System.out.println ("W: no response from server, retrying…\n");
                    //  Old socket is confused; close it and open a new one
                    ctx.destroySocket(client);
                    System.out.println ("I: reconnecting to server…\n");
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
