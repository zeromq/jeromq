package zmq;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestShutdownStress
{
    private static final int THREAD_COUNT = 100;

    class Worker implements Runnable
    {
        int port;
        SocketBase s;

        Worker(SocketBase s) throws IOException
        {
            this.port = Utils.findOpenPort();
            this.s = s;
        }

        @Override
        public void run()
        {
            boolean rc = ZMQ.connect(s, "tcp://127.0.0.1:" + port);
            assertThat(rc, is(true));

            //  Start closing the socket while the connecting process is underway.
            ZMQ.close(s);
        }
    }

    @Test
    public void testShutdownStress() throws Exception
    {
        Thread[] threads = new Thread[THREAD_COUNT];

        int randomPort = Utils.findOpenPort();

        for (int j = 0; j != 10; j++) {
            Ctx ctx = ZMQ.init(7);
            assertThat(ctx, notNullValue());

            SocketBase s1 = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
            assertThat(s1, notNullValue());

            boolean rc = ZMQ.bind(s1, "tcp://127.0.0.1:" + randomPort);
            assertThat(rc, is(true));

            for (int i = 0; i != THREAD_COUNT; i++) {
                SocketBase s2 = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
                assert (s2 != null);
                threads[i] = new Thread(new Worker(s2));
                threads[i].start();
            }

            for (int i = 0; i != THREAD_COUNT; i++) {
                threads[i].join();
            }

            ZMQ.close(s1);
            ZMQ.term(ctx);
        }
    }
}
