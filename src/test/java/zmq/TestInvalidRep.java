package zmq;

import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestInvalidRep {
    //  Create REQ/ROUTER wiring.
    
    @Test
    public void testInvalidRep () {
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());
        SocketBase router_socket = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_ROUTER);
        assertThat (router_socket, notNullValue());
        
        SocketBase req_socket = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_REQ);
        assertThat (req_socket, notNullValue());
        int linger = 0;
        int rc;
        ZMQ.zmq_setsockopt (router_socket, ZMQ.ZMQ_LINGER, linger);
        ZMQ.zmq_setsockopt (req_socket, ZMQ.ZMQ_LINGER, linger);
        rc = ZMQ.zmq_bind (router_socket, "inproc://hi");
        assertThat (rc , is(0));
        boolean brc = ZMQ.zmq_connect (req_socket, "inproc://hi");
        assertThat (brc, is(true) );
    
        //  Initial request.
        rc = ZMQ.zmq_send (req_socket, "r", 0);
        assert (rc == 1);
    
        //  Receive the request.
        Msg addr ;
        Msg bottom ;
        Msg body ;
        addr = ZMQ.zmq_recv (router_socket, 0);
        int addr_size = addr.size();
        System.out.println("addr_size: " + addr.size());
        assertThat (addr.size() >= 0, is(true));
        bottom = ZMQ.zmq_recv (router_socket,  0);
        assertThat (bottom.size(), is(0));
        body = ZMQ.zmq_recv (router_socket,  0);
        assertThat (body.size(), is(1));
        assertThat (body.data().get(), is((byte)'r'));
        
        //  Send invalid reply.
        rc = ZMQ.zmq_send (router_socket, addr, 0);
        assertThat (rc, is(addr_size));
        //  Send valid reply.
        rc = ZMQ.zmq_send (router_socket, addr, ZMQ.ZMQ_SNDMORE);
        assertThat (rc, is(addr_size));
        rc = ZMQ.zmq_send (router_socket, bottom, ZMQ.ZMQ_SNDMORE);
        assertThat (rc, is( 0));
        rc = ZMQ.zmq_send (router_socket, "b", 0);
        assertThat (rc, is( 1));
    
        
        //  Check whether we've got the valid reply.
        body = ZMQ.zmq_recv (req_socket, 0);
        assertThat (body.size(), is( 1));
        assertThat (body.data().get() , is((byte)'b'));
    
        //  Tear down the wiring.
        rc = ZMQ.zmq_close (router_socket);
        assertThat (rc ,is(0));
        rc = ZMQ.zmq_close (req_socket);
        assertThat (rc ,is(0));
        rc = ZMQ.zmq_term (ctx);
        assertThat (rc ,is(0));
    }
}
