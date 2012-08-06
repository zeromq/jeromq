package zmq;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestConnectResolve {

	@Test
	public void testConnectResolve() {
	    System.out.println( "test_connect_resolve running...\n");
	
	    Ctx ctx = ZMQ.zmq_init (1);
	    assertThat (ctx, notNullValue());
	
	    //  Create pair of socket, each with high watermark of 2. Thus the total
	    //  buffer space should be 4 messages.
	    SocketBase sock = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PUB);
	    assertThat (sock, notNullValue());
	
	    
	    int rc = ZMQ.zmq_connect (sock, "tcp://localhost:1234");
	    assertThat (rc, is(0));
	

	    try {
	        rc = ZMQ.zmq_connect (sock, "tcp://foobar123xyz:1234");
	        assertTrue(false);
	    } catch (IllegalArgumentException e) {
	    }
	    
	    
	    rc = ZMQ.zmq_close (sock);
	    assertThat (rc, is(0));
	
	    
	    rc = ZMQ.zmq_term (ctx);
	    assertThat (rc, is(0));
	    
	}
}
