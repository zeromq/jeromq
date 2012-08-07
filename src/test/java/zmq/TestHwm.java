package zmq;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestHwm {

    @Test
    public void testHwm() {
        Ctx ctx = ZMQ.zmq_init (1);
        assertThat (ctx, notNullValue());

        int rc = 0;
        boolean brc = false;
        //  Create pair of socket, each with high watermark of 2. Thus the total
        //  buffer space should be 4 messages.
        SocketBase sb = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PULL);
        assertThat (sb, notNullValue());
        int hwm = 2;
        ZMQ.zmq_setsockopt (sb, ZMQ.ZMQ_RCVHWM, hwm);
        
        rc = ZMQ.zmq_bind (sb, "inproc://a");
        assertThat (rc, is(0));
        
        SocketBase sc = ZMQ.zmq_socket (ctx, ZMQ.ZMQ_PUSH);
        assertThat (sc, notNullValue());
        
        ZMQ.zmq_setsockopt (sc, ZMQ.ZMQ_SNDHWM, hwm);
        
        brc = ZMQ.zmq_connect (sc, "inproc://a");
        assertThat (rc, is(0));

        
        //  Try to send 10 messages. Only 4 should succeed.
        for (int i = 0; i < 10; i++)
        {
            rc = ZMQ.zmq_send (sc, null, 0, ZMQ.ZMQ_DONTWAIT);
            if (i < 4)
                assertThat (rc, is(0));
            else
                assertThat (rc, is(-1));
        }

        
        // There should be now 4 messages pending, consume them.
        for (int i = 0; i != 4; i++) {
            rc = ZMQ.zmq_recv (sb, null, 0, 0);
            assertThat (rc, is(0));
        }

        
        // Now it should be possible to send one more.
        rc = ZMQ.zmq_send (sc, null, 0, 0);
        assertThat (rc, is(0));

        //  Consume the remaining message.
        rc = ZMQ.zmq_recv (sb, null, 0, 0);
        assertThat (rc, is(0));
        rc = ZMQ.zmq_close (sc);
        assertThat (rc, is(0));

        rc = ZMQ.zmq_close (sb);
        assertThat (rc, is(0));

        rc = ZMQ.zmq_term (ctx);
        assertThat (rc, is(0));
        

    }
}
