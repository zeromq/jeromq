package zmq;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestConnectResolve
{
    @Test
    public void testConnectResolve() throws IOException
    {
        int port = Utils.findOpenPort();
        System.out.println("test_connect_resolve running...\n");

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Create pair of socket, each with high watermark of 2. Thus the total
        //  buffer space should be 4 messages.
        SocketBase sock = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(sock, notNullValue());

        boolean brc = ZMQ.connect(sock, "tcp://localhost:" + port);
        assertThat(brc, is(true));

        /*
        try {
            brc = ZMQ.connect (sock, "tcp://foobar123xyz:" + port);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
        */

        ZMQ.close(sock);
        ZMQ.term(ctx);
    }
}
