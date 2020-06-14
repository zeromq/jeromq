package zmq.socket.reqrep;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;

public class TestInvalidRep
{
    //  Create REQ/ROUTER wiring.
    @Test
    public void testInvalidRep()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase routerSocket = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(routerSocket, notNullValue());

        SocketBase reqSocket = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(reqSocket, notNullValue());
        int linger = 0;
        int rc;
        ZMQ.setSocketOption(routerSocket, ZMQ.ZMQ_LINGER, linger);
        ZMQ.setSocketOption(reqSocket, ZMQ.ZMQ_LINGER, linger);
        boolean brc = ZMQ.bind(routerSocket, "inproc://hi");
        assertThat(brc, is(true));
        brc = ZMQ.connect(reqSocket, "inproc://hi");
        assertThat(brc, is(true));

        //  Initial request.
        rc = ZMQ.send(reqSocket, "r", 0);
        assertThat(rc, is(1));

        //  Receive the request.
        Msg addr;
        Msg bottom;
        Msg body;
        addr = ZMQ.recv(routerSocket, 0);
        int addrSize = addr.size();
        System.out.println("addrSize: " + addr.size());
        assertThat(addr.size() > 0, is(true));
        bottom = ZMQ.recv(routerSocket, 0);
        assertThat(bottom.size(), is(0));
        body = ZMQ.recv(routerSocket, 0);
        assertThat(body.size(), is(1));
        assertThat(body.data()[0], is((byte) 'r'));

        //  Send invalid reply.
        rc = ZMQ.send(routerSocket, addr, 0);
        assertThat(rc, is(addrSize));
        //  Send valid reply.
        rc = ZMQ.send(routerSocket, addr, ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(addrSize));
        rc = ZMQ.send(routerSocket, bottom, ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(0));
        rc = ZMQ.send(routerSocket, "b", 0);
        assertThat(rc, is(1));

        //  Check whether we've got the valid reply.
        body = ZMQ.recv(reqSocket, 0);
        assertThat(body.size(), is(1));
        assertThat(body.data()[0], is((byte) 'b'));

        //  Tear down the wiring.
        ZMQ.close(routerSocket);
        ZMQ.close(reqSocket);
        ZMQ.term(ctx);
    }
}
