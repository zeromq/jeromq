package zmq.proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class ProxySingleSocketTest
{
    private static class ServerTask implements Runnable
    {
        private final Ctx    ctx;
        private final String host;

        public ServerTask(Ctx ctx, String host)
        {
            this.ctx = ctx;
            this.host = host;
        }

        @Override
        public void run()
        {
            SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
            assertThat(rep, notNullValue());

            boolean rc = ZMQ.bind(rep, host);
            assertThat(rc, is(true));

            // Control socket receives terminate command from main over inproc
            SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
            ZMQ.setSocketOption(control, ZMQ.ZMQ_SUBSCRIBE, "");

            rc = ZMQ.connect(control, "inproc://control");
            assertThat(rc, is(true));

            // Use rep as both frontend and backend
            ZMQ.proxy(rep, rep, null, control);

            ZMQ.close(rep);
            ZMQ.close(control);
        }

    }

    @Test
    public void testProxySingleSocket() throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;

        // The main thread simply starts several clients and a server, and then
        // waits for the server to finish.
        Ctx ctx = ZMQ.createContext();

        SocketBase req = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());

        boolean rc = ZMQ.connect(req, host);
        assertThat(rc, is(true));

        // Control socket receives terminate command from main over inproc
        SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);

        rc = ZMQ.bind(control, "inproc://control");
        assertThat(rc, is(true));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new ServerTask(ctx, host));

        int ret = ZMQ.send(req, "msg1", 0);
        assertThat(ret, is(4));

        System.out.print(".");
        Msg msg = ZMQ.recv(req, 0);
        System.out.print(".");
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data(), ZMQ.CHARSET), is("msg1"));

        ret = ZMQ.send(req, "msg22", 0);
        assertThat(ret, is(5));

        System.out.print(".");
        msg = ZMQ.recv(req, 0);
        System.out.print(".");
        assertThat(msg, notNullValue());
        assertThat(new String(msg.data(), ZMQ.CHARSET), is("msg22"));

        ret = ZMQ.send(control, ZMQ.PROXY_TERMINATE, 0);
        assertThat(ret, is(9));

        System.out.println(".");
        ZMQ.close(control);
        ZMQ.close(req);

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        ZMQ.term(ctx);
    }
}
