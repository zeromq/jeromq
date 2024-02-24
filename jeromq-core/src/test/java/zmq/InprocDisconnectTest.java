package zmq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.channels.Selector;

import org.junit.Test;

import zmq.poll.PollItem;

public class InprocDisconnectTest
{
    @Test
    public void testDisconnectInproc()
    {
        Ctx context = ZMQ.createContext();
        Selector selector = context.createSelector();
        try {
            testDisconnectInproc(context, selector);
        }
        finally {
            context.closeSelector(selector);
        }
        ZMQ.term(context);
    }

    private void testDisconnectInproc(Ctx context, Selector selector)
    {
        int publicationsReceived = 0;
        boolean isSubscribed = false;

        SocketBase pubSocket = ZMQ.socket(context, ZMQ.ZMQ_XPUB);
        SocketBase subSocket = ZMQ.socket(context, ZMQ.ZMQ_SUB);

        ZMQ.setSocketOption(subSocket, ZMQ.ZMQ_SUBSCRIBE, "foo".getBytes());
        ZMQ.bind(pubSocket, "inproc://someInProcDescriptor");

        int more;
        int iteration = 0;

        while (true) {
            PollItem[] items = { new PollItem(subSocket, ZMQ.ZMQ_POLLIN), // read publications
                    new PollItem(pubSocket, ZMQ.ZMQ_POLLIN) // read subscriptions
            };
            int rc = ZMQ.poll(selector, items, 2, 100L);

            if (items[1].isReadable()) {
                while (true) {
                    Msg msg = ZMQ.recv(pubSocket, 0);
                    int msgSize = msg.size();
                    byte[] buffer = msg.data();

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

                    more = ZMQ.getSocketOption(pubSocket, ZMQ.ZMQ_RCVMORE);

                    if (more == 0) {
                        break; //  Last message part
                    }
                }
            }

            if (items[0].isReadable()) {
                while (true) {
                    Msg msg = ZMQ.recv(subSocket, 0);
                    int msgSize = msg.size();
                    byte[] buffer = msg.data();

                    System.out.printf("received on subscriber '%s'\n", new String(buffer, 0, msgSize));

                    more = ZMQ.getSocketOption(subSocket, ZMQ.ZMQ_RCVMORE);

                    if (more == 0) {
                        publicationsReceived++;
                        break; //  Last message part
                    }
                }
            }

            if (iteration == 1) {
                ZMQ.connect(subSocket, "inproc://someInProcDescriptor");
            }

            if (iteration == 4) {
                ZMQ.disconnect(subSocket, "inproc://someInProcDescriptor");
            }

            if (iteration > 4 && rc == 0) {
                break;
            }

            Msg channelEnvlp = new Msg("foo".getBytes(ZMQ.CHARSET));
            ZMQ.sendMsg(pubSocket, channelEnvlp, ZMQ.ZMQ_SNDMORE);

            Msg message = new Msg("this is foo!".getBytes(ZMQ.CHARSET));
            ZMQ.sendMsg(pubSocket, message, 0);
            iteration++;
        }

        assertEquals(3, publicationsReceived);
        assertFalse(isSubscribed);

        ZMQ.close(pubSocket);
        ZMQ.close(subSocket);
    }
}
