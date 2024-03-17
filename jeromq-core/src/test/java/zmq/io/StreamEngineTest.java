package zmq.io;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Config;
import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class StreamEngineTest
{
    @Test
    public void testEncoderFlipIssue520()
    {
        Ctx ctx = ZMQ.createContext();
        assertThat(ctx, notNullValue());

        SocketBase sender = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(sender, notNullValue());

        boolean rc = ZMQ.setSocketOption(sender, ZMQ.ZMQ_IMMEDIATE, false);
        assertThat(rc, is(true));

        SocketBase receiver = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(receiver, notNullValue());

        String addr = "tcp://localhost:*";

        rc = ZMQ.bind(receiver, addr);
        assertThat(rc, is(true));

        addr = (String) ZMQ.getSocketOptionExt(receiver, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(addr, notNullValue());

        rc = ZMQ.connect(sender, addr);
        assertThat(rc, is(true));

        final int headerSize = 8 + 1;
        // first message + second message header fill the buffer
        byte[] msg1 = msg(Config.OUT_BATCH_SIZE.getValue() - 2 * headerSize);
        // second message will go in zero-copy mode
        byte[] msg2 = msg(Config.OUT_BATCH_SIZE.getValue());

        exchange(sender, receiver, msg1, msg2, msg1, msg2);

        ZMQ.close(receiver);
        ZMQ.close(sender);
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

    private void exchange(SocketBase sender, SocketBase receiver, byte[]... msgs)
    {
        int repetition = 50;
        for (int idx = 0; idx < repetition; ++idx) {
            for (byte[] msg : msgs) {
                int num = ZMQ.send(sender, msg, 0);
                assertThat(num, is(msg.length));
            }
        }
        for (int idx = 0; idx < repetition; ++idx) {
            for (byte[] msg : msgs) {
                Msg received = ZMQ.recv(receiver, 0);
                assertThat(received.data(), is(msg));
            }
        }
    }
}
