package zmq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestDisconnectInproc
{
    @Test
    public void testDisconnectInproc() throws Exception
    {
        int publicationsReceived = 0;
        boolean isSubscribed = false;

        Ctx context = ZMQ.zmq_ctx_new();
        SocketBase pubSocket = ZMQ.zmq_socket(context, ZMQ.ZMQ_XPUB);
        SocketBase subSocket = ZMQ.zmq_socket(context, ZMQ.ZMQ_SUB);

        ZMQ.zmq_setsockopt(subSocket, ZMQ.ZMQ_SUBSCRIBE, "foo".getBytes());
        ZMQ.zmq_bind(pubSocket, "inproc://someInProcDescriptor");

        int more;
        int iteration = 0;

        while(true) {
            PollItem items [] = {
                    new PollItem(subSocket, ZMQ.ZMQ_POLLIN), // read publications
                    new PollItem(pubSocket, ZMQ.ZMQ_POLLIN) // read subscriptions
            };
            ZMQ.zmq_poll(items, 2, 500);

            if (items[1].isReadable()) {
                while (true) {
                    Msg msg = ZMQ.zmq_recv(pubSocket, 0);
                    int msgSize = msg.size();
                    byte[] buffer = msg.data();

                    if (buffer[0] == 0) {
                        assertTrue(isSubscribed);
                        System.out.printf("unsubscribing from '%s'\n", new String(buffer, 1, msgSize - 1));
                        isSubscribed = false;
                    } else {
                        assert(!isSubscribed);
                        System.out.printf("subscribing on '%s'\n", new String(buffer, 1, msgSize - 1));
                        isSubscribed = true;
                    }

                    more = ZMQ.zmq_getsockopt(pubSocket, ZMQ.ZMQ_RCVMORE);

                    if (more == 0)
                        break;      //  Last message part
                }
            }

            if (items[0].isReadable()) {
                while (true) {
                    Msg msg = ZMQ.zmq_recv(subSocket, 0);
                    int msgSize = msg.size();
                    byte[] buffer = msg.data();

                    System.out.printf("received on subscriber '%s'\n", new String(buffer, 0, msgSize));

                    more = ZMQ.zmq_getsockopt(subSocket, ZMQ.ZMQ_RCVMORE);

                    if (more == 0) {
                        publicationsReceived++;
                        break;      //  Last message part
                    }
                }
            }

            if (iteration == 1) {
                ZMQ.zmq_connect(subSocket, "inproc://someInProcDescriptor");
            }

            if (iteration == 4) {
                ZMQ.zmq_disconnect(subSocket, "inproc://someInProcDescriptor");
            }

            if (iteration == 10) {
                break;
            }

            Msg channelEnvlp = new Msg("foo".getBytes(ZMQ.CHARSET));
            ZMQ.zmq_sendmsg(pubSocket, channelEnvlp, ZMQ.ZMQ_SNDMORE);

            Msg message = new Msg("this is foo!".getBytes(ZMQ.CHARSET));
            ZMQ.zmq_sendmsg(pubSocket, message, 0);
            iteration++;
        }

        assertEquals(3, publicationsReceived);
        assertTrue(!isSubscribed);

        ZMQ.zmq_close(pubSocket);
        ZMQ.zmq_close(subSocket);

        ZMQ.zmq_term(context);
    }
}
