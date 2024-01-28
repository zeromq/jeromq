package zmq.proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import zmq.Ctx;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

public class ProxyTerminateTest
{
    private static class ServerTask implements Runnable
    {
        private final Ctx    ctx;
        private final CompletableFuture<Boolean> resultHander;
        private final String hostFrontend;
        private final String       hostBackend;

        public ServerTask(Ctx ctx, String hostFrontend, String hostBackend, CompletableFuture<Boolean> resultHander)
        {
            this.ctx = ctx;
            this.hostFrontend = hostFrontend;
            this.hostBackend = hostBackend;
            this.resultHander = resultHander;
        }

        @Override
        public void run()
        {
            SocketBase frontend = null;
            // Nice socket which is never read
            SocketBase backend = null;
            // Control socket receives terminate command from main over inproc
            SocketBase control = null;
            try {
                frontend = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
                assertThat(frontend, notNullValue());
                ZMQ.setSocketOption(frontend, ZMQ.ZMQ_SUBSCRIBE, "");
                boolean rc = ZMQ.bind(frontend, hostFrontend);
                assertThat(rc, is(true));
                backend = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
                assertThat(backend, notNullValue());
                rc = ZMQ.bind(frontend, hostBackend);
                assertThat(rc, is(true));
                control = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
                ZMQ.setSocketOption(control, ZMQ.ZMQ_SUBSCRIBE, "");
                rc = ZMQ.connect(control, "inproc://control");
                assertThat(rc, is(true));
                // Connect backend to frontend via a proxy
                ZMQ.proxy(frontend, backend, null, control);
                resultHander.complete(true);
            }
            finally {
                if (frontend != null) {
                    ZMQ.close(frontend);
                }
                if (backend != null) {
                    ZMQ.close(backend);
                }
                if (control != null) {
                    ZMQ.close(control);
                }
            }
        }

    }

    @Test(timeout = 5000)
    public void testProxyTerminate() throws IOException, InterruptedException, ExecutionException
    {
        int port = Utils.findOpenPort();
        String frontend = "tcp://127.0.0.1:" + port;
        port = Utils.findOpenPort();
        String backend = "tcp://127.0.0.1:" + port;

        // The main thread simply starts a basic steerable proxy server, publishes some messages, and then
        // waits for the server to terminate.
        Ctx ctx = ZMQ.createContext();

        // Control socket receives terminate command from main over inproc
        SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);

        boolean rc = ZMQ.bind(control, "inproc://control");
        assertThat(rc, is(true));

        CompletableFuture<Boolean> resultHander = new CompletableFuture<>();
        Thread thread = new Thread(new ServerTask(ctx, frontend, backend, resultHander));
        thread.setUncaughtExceptionHandler((t, e) -> resultHander.completeExceptionally(e));
        thread.start();

        Thread.sleep(500);

        // Start a secondary publisher which writes data to the SUB-PUSH server socket
        SocketBase publisher = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(publisher, notNullValue());

        rc = ZMQ.connect(publisher, frontend);
        assertThat(rc, is(true));

        Thread.sleep(50);

        int ret = ZMQ.send(publisher, "This is a test", 0);
        assertThat(ret, is(14));

        Thread.sleep(50);

        ret = ZMQ.send(publisher, "This is a test", 0);
        assertThat(ret, is(14));

        Thread.sleep(50);

        ret = ZMQ.send(publisher, "This is a test", 0);
        assertThat(ret, is(14));

        ret = ZMQ.send(control, ZMQ.PROXY_TERMINATE, 0);
        assertThat(ret, is(9));

        ZMQ.close(publisher);
        ZMQ.close(control);

        resultHander.get();
        ZMQ.term(ctx);

    }
}
