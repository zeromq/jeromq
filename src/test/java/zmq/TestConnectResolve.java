package zmq;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestConnectResolve
{
    @Test
    public void testConnectResolve()
    {
        System.out.println("test_connect_resolve running...\n");

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Create pair of socket, each with high watermark of 2. Thus the total
        //  buffer space should be 4 messages.
        SocketBase sock = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(sock, notNullValue());

        boolean brc = ZMQ.connect(sock, "tcp://localhost:1234");
        assertThat(brc, is(true));

        /*
        try {
            brc = ZMQ.connect (sock, "tcp://foobar123xyz:1234");
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
        */

        ZMQ.close(sock);
        ZMQ.term(ctx);
    }
}
