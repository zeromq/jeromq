package zmq.socket;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class AbstractSpecTest
{
    public boolean sendSeq(SocketBase socket, String... data)
    {
        int rc = 0;
        if (data.length == 0) {
            return false;
        }
        for (int idx = 0; idx < data.length; ++idx) {
            String payload = data[idx];

            if (payload == null) {
                rc = ZMQ.send(socket, new Msg(), idx == data.length - 1 ? 0 : ZMQ.ZMQ_SNDMORE);
                assertThat(rc < 0, is(false));
            }
            else {
                Msg msg = new Msg(payload.getBytes(ZMQ.CHARSET));
                rc = ZMQ.send(socket, msg, idx == data.length - 1 ? 0 : ZMQ.ZMQ_SNDMORE);
                assertThat(rc < 0, is(false));
            }
        }
        return rc >= 0;
    }

    public void recvSeq(SocketBase socket, String... data)
    {
        for (int idx = 0; idx < data.length; ++idx) {
            Msg msg = ZMQ.recv(socket, 0);
            String payload = data[idx];

            if (payload == null) {
                assertThat(msg, notNullValue());
                assertThat(msg.size(), is(0));
            }
            else {
                assertThat(msg, notNullValue());
                assertThat(msg.data(), is(payload.getBytes(ZMQ.CHARSET)));
            }

            int rc = ZMQ.getSocketOption(socket, ZMQ.ZMQ_RCVMORE);
            if (idx == data.length - 1) {
                assertThat(rc, is(0));
            }
            else {
                assertThat(rc, is(1));
            }
        }
    }
}
