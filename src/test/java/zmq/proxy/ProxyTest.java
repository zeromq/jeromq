package zmq.proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.poll.PollItem;
import zmq.util.Utils;

// Asynchronous client-to-server (DEALER to ROUTER) - pure libzmq
//
// While this example runs in a single process, that is to make
// it easier to start and stop the example. Each task may have its own
// context and conceptually acts as a separate process. To have this
// behaviour, it is necessary to replace the inproc transport of the
// control socket by a tcp transport.

// This is our client task
// It connects to the server, and then sends a request once per second
// It collects responses as they arrive, and it prints them out. We will
// run several client tasks in parallel, each with a different random ID.
public class ProxyTest
{
    private static final class Client implements Callable<Boolean>
    {
        private final String  host;
        private final String  controlEndpoint;
        private final boolean verbose;
        private final CountDownLatch started;

        Client(String host, String controlEndpoint, boolean verbose)
        {
            this.host = host;
            this.controlEndpoint = controlEndpoint;
            this.verbose = verbose;
            this.started = new CountDownLatch(1);
        }

        @Override
        public Boolean call()
        {
            Ctx ctx = ZMQ.createContext();
            SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            assertThat(client, notNullValue());

            // Control socket receives terminate command from main over inproc
            SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
            assertThat(control, notNullValue());

            boolean rc = ZMQ.setSocketOption(control, ZMQ.ZMQ_SUBSCRIBE, ZMQ.SUBSCRIPTION_ALL);
            assertThat(rc, is(true));

            rc = ZMQ.connect(control, this.controlEndpoint);
            assertThat(rc, is(true));

            String identity = UUID.randomUUID().toString();
            rc = ZMQ.setSocketOption(client, ZMQ.ZMQ_IDENTITY, identity);
            assertThat(rc, is(true));

            rc = ZMQ.connect(client, host);
            assertThat(rc, is(true));

            PollItem[] items = new PollItem[2];
            items[0] = new PollItem(client, ZMQ.ZMQ_POLLIN);
            items[1] = new PollItem(control, ZMQ.ZMQ_POLLIN);

            int requestNbr = 0;
            boolean run = true;

            Selector selector = ctx.createSelector();
            started.countDown();
            while (run && ! Thread.currentThread().isInterrupted()) {
                // Tick once per 200 ms, pulling in arriving messages
                for (int centitick = 0; centitick < 20; centitick++) {
                    int count = ZMQ.poll(selector, items, 10);
                    if (items[0].isReadable()) {
                        count--;
                        Msg msgrsp = ZMQ.recv(client, 0);

                        String payload = new String(msgrsp.data(), ZMQ.CHARSET);
                        if (verbose) {
                            System.out.printf("%1$s Client received %2$s%n", identity, payload);
                        }

                        //  Check that message is still the same
                        assertThat(payload.startsWith(identity + " Request #"), is(true));

                        int more = ZMQ.getSocketOption(client, ZMQ.ZMQ_RCVMORE);
                        assertThat(more, is(0));
                    }
                    if (items[1].isReadable()) {
                        count--;
                        Msg msgin = ZMQ.recv(control, 0);
                        if (Arrays.equals(msgin.data(), ZMQ.PROXY_TERMINATE)) {
                            run = false;
                            break;
                        }
                    }
                    assertThat(count, is(0));
                }
                String payload = String.format("%1$s Request #%2$s", identity, ++requestNbr);
                Msg msgout = new Msg(payload.getBytes(ZMQ.CHARSET));
                int sent = ZMQ.send(client, msgout, 0);
                assertThat(sent, is(msgout.size()));
                if (verbose) {
                    System.out.printf("%1$s Sent payload %2$s%n", identity, payload);
                }
            }
            ctx.closeSelector(selector);

            ZMQ.close(control);
            ZMQ.close(client);
            ZMQ.term(ctx);

            return true;
        }
    }

    private static final String BACKEND = "inproc://backend";

    private static final class Server implements Callable<Boolean>
    {
        private final String  host;
        private final String  controlEndpoint;
        private final boolean verbose;
        private final CountDownLatch started;

        Server(String host, String controlEndpoint, boolean verbose)
        {
            this.host = host;
            this.controlEndpoint = controlEndpoint;
            this.verbose = verbose;
            this.started = new CountDownLatch(1);
        }

        @Override
        public Boolean call() throws InterruptedException, ExecutionException, TimeoutException
        {
            Ctx ctx = ZMQ.createContext();
            // Frontend socket talks to clients over TCP
            SocketBase frontend = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
            assertThat(frontend, notNullValue());

            boolean rc = ZMQ.bind(frontend, host);
            assertThat(rc, is(true));

            // Backend socket talks to workers over inproc
            SocketBase backend = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            assertThat(backend, notNullValue());

            rc = ZMQ.bind(backend, BACKEND);
            assertThat(rc, is(true));

            // Control socket receives terminate command from main over inproc
            SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
            assertThat(control, notNullValue());

            rc = ZMQ.setSocketOption(control, ZMQ.ZMQ_SUBSCRIBE, ZMQ.SUBSCRIPTION_ALL);
            assertThat(rc, is(true));

            rc = ZMQ.connect(control, this.controlEndpoint);
            assertThat(rc, is(true));

            // Launch pool of worker threads, precise number is not critical
            int count = 5;
            ExecutorService executor = Executors.newFixedThreadPool(count);
            List<Future<Boolean>> workers = new ArrayList<>(count);
            for (int idx = 0; idx < count; ++idx) {
                Worker w = new Worker(ctx, idx, controlEndpoint, verbose);
                workers.add(executor.submit(w));
                w.started.await();
            }

            started.countDown();
            // Connect backend to frontend via a proxy
            ZMQ.proxy(frontend, backend, null, control);

            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                fail("Hanged tasks");
            }

            ZMQ.close(frontend);
            ZMQ.close(backend);
            ZMQ.close(control);

            ZMQ.term(ctx);
            boolean completed = true;
            for (Future<Boolean> f : workers) {
                completed &= f.get(3, TimeUnit.SECONDS);
            }
            return completed;
       }
    }

    // Each worker task works on one request at a time and sends a random number
    // of replies back, with random delays between replies:
    // The comments in the first column, if suppressed, makes it a poller version
    private static final class Worker implements Callable<Boolean>
    {
        private final boolean verbose;
        private final int     idx;
        private final String  control;
        private final Ctx     ctx;
        private final CountDownLatch started;

        public Worker(Ctx ctx, int idx, String control, boolean verbose)
        {
            this.ctx = ctx;
            this.idx = idx;
            this.control = control;
            this.verbose = verbose;
            this.started = new CountDownLatch(1);
       }

        @Override
        public Boolean call()
        {
            SocketBase worker = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
            assertThat(worker, notNullValue());

            boolean rc = ZMQ.connect(worker, BACKEND);
            assertThat(rc, is(true));

            // Control socket receives terminate command from main over inproc
            SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
            assertThat(control, notNullValue());

            rc = ZMQ.setSocketOption(control, ZMQ.ZMQ_SUBSCRIBE, new byte[0]);
            assertThat(rc, is(true));

            rc = ZMQ.connect(control, this.control);
            assertThat(rc, is(true));

            boolean run = true;
            Random random = new Random();

            Msg msg;
            started.countDown();
            while (run) {
                msg = ZMQ.recv(control, ZMQ.ZMQ_DONTWAIT);
                if (control.errno() == ZError.ETERM) {
                    break;
                }
                if (msg != null) {
                    if (Arrays.equals(msg.data(), ZMQ.PROXY_TERMINATE)) {
                        break;
                    }
                }

                // The DEALER socket gives us the reply envelope and message
                // if we don't poll, we have to use ZMQ_DONTWAIT, if we poll, we can block-receive with 0

                Msg identity = ZMQ.recv(worker, ZMQ.ZMQ_DONTWAIT);
                if (identity != null) {
                    msg = ZMQ.recv(worker, 0);
                    if (verbose) {
                        System.out.printf("Worker #%1$s received %2$s%n", idx, msg);
                    }

                    // Send 0..4 replies back
                    for (int idx = 0; idx < random.nextInt(5); ++idx) {
                        // Sleep for some fraction of a second
                        ZMQ.msleep(random.nextInt(10) + 1);

                        //  Send message from server to client
                        int sent = ZMQ.send(worker, identity, ZMQ.ZMQ_SNDMORE);
                        assertThat(sent, is(identity.size()));

                        sent = ZMQ.send(worker, msg, 0);
                        assertThat(sent, is(msg.size()));
                    }
                }
            }

            ZMQ.close(control);
            ZMQ.close(worker);
            return true;
        }
    }

    @Test(timeout = 10000)
    public void testProxy() throws Throwable
    {
        // The main thread simply starts several clients and a server, and then
        // waits for the server to finish.
        Ctx ctx = ZMQ.createContext();

        String controlEndpoint = "tcp://localhost:" + Utils.findOpenPort();

        // Control socket receives terminate command from main over inproc
        SocketBase control = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(control, notNullValue());

        boolean rc = ZMQ.bind(control, controlEndpoint);
        assertThat(rc, is(true));

        String host = "tcp://127.0.0.1:" + Utils.findOpenPort();
        int count = 5;
        ExecutorService executor = Executors.newFixedThreadPool(count + 1);

        Server server = new Server(host, controlEndpoint, false);
        Future<Boolean> fserver = executor.submit(server);
        server.started.await();

        List<Future<Boolean>> clientsf = new ArrayList<>(count);
        for (int idx = 0; idx < count; ++idx) {
            Client client = new Client(host, controlEndpoint, false);
            clientsf.add(executor.submit(client));
            client.started.await();
        }

        while (true) {
            Thread.sleep(100);
            int sent = ZMQ.send(control, ZMQ.PROXY_TERMINATE, 0);
            assertThat(sent, is(9));
            if (clientsf.get(4).isDone()) {
                break;
            }
        }
        for (Future<Boolean> client : clientsf) {
            try {
                assertThat(client.get(), is(true));
            }
            catch (ExecutionException e) {
                e.getCause().printStackTrace();
                throw e.getCause();
            }
        }

        ZMQ.close(control);

        executor.shutdown();
        if (! executor.awaitTermination(4, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            fail("Hanged tasks");
       }

        ZMQ.term(ctx);

        try {
            assertThat(fserver.get(), is(true));
        }
        catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
