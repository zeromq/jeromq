package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class DealerDealerTest
{
    @Test
    public void testIssue131() throws IOException
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase sender = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(sender, notNullValue());

        final int port = Utils.findOpenPort();
        final String addr = "tcp://localhost:" + port;

        boolean rc = ZMQ.connect(sender, addr);
        assertThat(rc, is(true));

        byte[] sbuf = msg(255);
        int sent = ZMQ.send(sender, sbuf, 0);
        assertThat(sent, is(255));

        byte[] quit = { 'q' };
        sent = ZMQ.send(sender, quit, 0);
        assertThat(sent, is(1));

        ZMQ.close(sender);

        SocketBase receiver = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(receiver, notNullValue());

        rc = ZMQ.bind(receiver, addr);
        assertThat(rc, is(true));

        int nbytes;
        do {
            Msg msg = ZMQ.recv(receiver, 0);
            nbytes = msg.size();
            System.out.println(msg);

        } while (nbytes != 1);

        ZMQ.close(receiver);
        ZMQ.term(ctx);
    }

    private byte[] msg(int length)
    {
        byte[] msg = new byte[length];
        for (int idx = 0; idx < msg.length; ++idx) {
            msg[idx] = (byte) idx;
        }
        return msg;
    }
}
