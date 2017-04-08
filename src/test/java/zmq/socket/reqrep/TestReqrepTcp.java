package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class TestReqrepTcp
{
    @Test
    public void testReqrepTcp() throws Exception
    {
        int port = Utils.findOpenPort();
        System.out.println("Starting with port " + port);
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase repBind = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(repBind, notNullValue());
        boolean rc = ZMQ.bind(repBind, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        SocketBase reqConnect = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(reqConnect, notNullValue());
        rc = ZMQ.connect(reqConnect, "tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        Helper.bounce(repBind, reqConnect);

        ZMQ.close(reqConnect);
        ZMQ.close(repBind);
        ZMQ.term(ctx);
    }
}
