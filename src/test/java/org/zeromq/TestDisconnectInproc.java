package zmq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.zeromq.ZContext;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class TestDisconnectInproc
{
    @Test
    public void testDisconnectInproc() throws Exception
    {
        int publicationsReceived = 0;
        boolean isSubscribed = false;

        ZContext ctx = new ZContext();
        Socket pubSocket = ctx.createSocket(ZMQ.ZMQ_XPUB);
        Socket subSocket = ctx.createSocket(ZMQ.ZMQ_SUB);

        subSocket.subscribe("foo".getBytes());
        pubSocket.bind("inproc://someInProcDescriptor");

        int iteration = 0;

        Poller poller = ctx.createPoller(2);
        poller.register(subSocket, Poller.POLLIN); // read publications
        poller.register(pubSocket, Poller.POLLIN); // read subscriptions

        while (true) {
            poller.poll(500);

            if (poller.pollin(1)) {
                while (true) {
                    byte[] buffer = pubSocket.recv(0);
                    int msgSize = buffer.length;

                    if (buffer[0] == 0) {
                        assertTrue(isSubscribed);
                        System.out.printf("unsubscribing from '%s'\n", new String(buffer, 1, msgSize - 1));
                        isSubscribed = false;
                    }
                    else {
                        assert (!isSubscribed);
                        System.out.printf("subscribing on '%s'\n", new String(buffer, 1, msgSize - 1));
                        isSubscribed = true;
                    }

                    if (!pubSocket.hasReceiveMore()) {
                        break;      //  Last message part
                    }
                }
            }

            if (poller.pollin(0)) {
                while (true) {
                    byte[] buffer = subSocket.recv(0);
                    int msgSize = buffer.length;

                    System.out.printf("received on subscriber '%s'\n", new String(buffer, 0, msgSize));

                    if (!subSocket.hasReceiveMore()) {
                        publicationsReceived++;
                        break;      //  Last message part
                    }
                }
            }

            if (iteration == 1) {
                subSocket.connect("inproc://someInProcDescriptor");
            }

            if (iteration == 4) {
                subSocket.disconnect("inproc://someInProcDescriptor");
            }

            if (iteration == 10) {
                break;
            }

            pubSocket.send("foo".getBytes(ZMQ.CHARSET), ZMQ.ZMQ_SNDMORE);
            pubSocket.send("this is foo!".getBytes(ZMQ.CHARSET), 0);
            iteration++;
        }

        assertEquals(3, publicationsReceived);
        assertTrue(!isSubscribed);

        ctx.destroy();
    }
}
