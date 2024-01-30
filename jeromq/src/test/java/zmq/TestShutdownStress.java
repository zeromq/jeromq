package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class TestShutdownStress
{
    private static final int THREAD_COUNT = 100;

    static class Worker implements Runnable
    {
        final SocketBase s;

        Worker(SocketBase s)
        {
            this.s = s;
        }

        @Override
        public void run()
        {
            boolean rc = ZMQ.connect(s, "tcp://127.0.0.1:*");
            assertThat(rc, is(true));

            //  Start closing the socket while the connecting process is underway.
            ZMQ.close(s);
        }
    }

    @Test
    public void testShutdownStress() throws Exception
    {
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int j = 0; j != 10; j++) {
            Ctx ctx = ZMQ.init(7);
            assertThat(ctx, notNullValue());

            SocketBase s1 = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
            assertThat(s1, notNullValue());

            boolean rc = ZMQ.bind(s1, "tcp://127.0.0.1:*");
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
