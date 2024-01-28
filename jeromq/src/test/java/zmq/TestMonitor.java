package zmq;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestMonitor
{
    static class SocketMonitor implements Callable<AssertionError>
    {
        private final Ctx            ctx;
        private final String         monitorAddr;
        private final CountDownLatch startLatch;
        private final CountDownLatch activeLatch;
        private final CountDownLatch monitorStopLatch;
        private       int            events;

        public SocketMonitor(Ctx ctx, String monitorAddr)
        {
            this.ctx = ctx;
            this.monitorAddr = monitorAddr;
            this.startLatch = new CountDownLatch(1);
            this.activeLatch = new CountDownLatch(1);
            this.monitorStopLatch = new CountDownLatch(1);
            events = 0;
        }

        @Override
        public AssertionError call()
        {
            SocketBase s = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            try {
                boolean rc = s.connect(monitorAddr);
                assertThat(rc, is(true));
                startLatch.countDown();
                // Only some exceptional events could fire
                while (true) {
                    ZMQ.Event event = ZMQ.Event.read(s);
                    // Signals the context termination
                    if (event == null && s.errno() == ZError.ETERM) {
                        break;
                    }
                    assertThat(event, notNullValue());
                    if (event.event == ZMQ.ZMQ_EVENT_CONNECTED || event.event == ZMQ.ZMQ_EVENT_ACCEPTED) {
                        activeLatch.countDown();
                    }
                    if (event.event == ZMQ.ZMQ_EVENT_MONITOR_STOPPED) {
                        monitorStopLatch.countDown();
                    }
                    switch (event.event) {
                    // listener specific
                    case ZMQ.ZMQ_EVENT_LISTENING:
                    case ZMQ.ZMQ_EVENT_ACCEPTED:
                        // connecter specific
                    case ZMQ.ZMQ_EVENT_CONNECTED:
                    case ZMQ.ZMQ_EVENT_CONNECT_DELAYED:
                        // generic - either end of the socket
                    case ZMQ.ZMQ_EVENT_CLOSE_FAILED:
                    case ZMQ.ZMQ_EVENT_CLOSED:
                    case ZMQ.ZMQ_EVENT_DISCONNECTED:
                    case ZMQ.ZMQ_EVENT_MONITOR_STOPPED:
                    case ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL:
                    case ZMQ.ZMQ_EVENT_CONNECT_RETRIED:
                        events |= event.event;
                        break;
                    default:
                        // out of band / unexpected event
                        fail("Unknown Event " + event.event);
                    }
                }
            }
            catch (AssertionError ex) {
                return ex;
            }
            finally {
                s.close();
            }
            return null;
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Test(timeout = 1000)
    public void testFailed()
    {
        String addr = "tcp://127.0.0.1:*";
        Ctx ctx = ZMQ.init(1);
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        try {
            assertThat(rep, notNullValue());
            boolean rc = ZMQ.monitorSocket(rep, addr, 0);
            assertThat(rc, is(false));
            assertThat(rep.errno.get(), is(ZError.EPROTONOSUPPORT));
        }
        finally {
            rep.close();
            ctx.terminate();
        }
    }

    @Test(timeout = 1000)
    public void testMonitor() throws Exception
    {
        String addr = "tcp://127.0.0.1:*";
        SocketMonitor[] threads = new SocketMonitor[3];
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        // set sockets monitors
        threads[0] = new SocketMonitor(ctx, "inproc://monitor.rep");
        threads[1] = new SocketMonitor(ctx, "inproc://monitor.req");
        threads[2] = new SocketMonitor(ctx, "inproc://monitor.req2");

        Future<AssertionError>[] futures = Arrays.stream(threads).map(executor::submit).toArray(Future[]::new);
        executor.shutdown();

        Arrays.stream(threads).map(sm -> sm.startLatch).forEach(this::timeOut);
        // Check that all tasks are still running
        assertThat(Arrays.stream(futures).filter(this::checkTaskAlive).count(), is(3L));

        // REP socket monitor, all events
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        boolean rc = ZMQ.monitorSocket(rep, "inproc://monitor.rep", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));
        rc = ZMQ.bind(rep, addr);
        assertThat(rc, is(true));

        // Resolve the effective listening address
        addr = (String) ZMQ.getSocketOptionExt(rep, ZMQ.ZMQ_LAST_ENDPOINT);
        assertThat(addr, notNullValue());

        // 1st REQ socket,  monitor all events
        SocketBase req = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());
        rc = ZMQ.monitorSocket(req, "inproc://monitor.req", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));
        rc = ZMQ.connect(req, addr);
        assertThat(rc, is(true));

        // 2nd REQ socket, monitor connected and monitor stop events only
        SocketBase req2 = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req2, notNullValue());
        rc = ZMQ.monitorSocket(req2, "inproc://monitor.req2", ZMQ.ZMQ_EVENT_CONNECTED | ZMQ.ZMQ_EVENT_MONITOR_STOPPED);
        assertThat(rc, is(true));
        rc = ZMQ.connect(req2, addr);
        assertThat(rc, is(true));

        // Plays the dialog between rep and req
        Arrays.stream(threads).map(sm -> sm.activeLatch).forEach(this::timeOut);
        Helper.bounce(rep, req);

        // Close the REP socket
        ZMQ.setSocketOption(rep, ZMQ.ZMQ_LINGER, 0);
        ZMQ.close(rep);

        // Close the REQ socket
        ZMQ.setSocketOption(req, ZMQ.ZMQ_LINGER, 0);
        ZMQ.close(req);

        // Close the 2nd REQ socket
        ZMQ.setSocketOption(req2, ZMQ.ZMQ_LINGER, 0);
        ZMQ.close(req2);

        // Wait for end of monitors, stopped by linger thread
        Arrays.stream(threads).map(sm -> sm.monitorStopLatch).forEach(this::timeOut);
        ZMQ.term(ctx);
        rc = executor.awaitTermination(1, TimeUnit.SECONDS);
        assertThat(rc, is(true));
        // Check that all tasks are finished
        assertThat(Arrays.stream(futures).filter(this::checkTaskAlive).count(), is(0L));

        // Expected REP socket events
        // We expect to at least observe these events
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_LISTENING) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_ACCEPTED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL) > 0);

        // Expected REQ socket events
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CONNECT_DELAYED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CONNECTED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL) > 0);

        // Expected 2nd REQ socket events
        assertEquals(ZMQ.ZMQ_EVENT_CONNECTED | ZMQ.ZMQ_EVENT_MONITOR_STOPPED, threads[2].events);
    }

    private boolean checkTaskAlive(Future<AssertionError> f)
    {
        try {
            if (f.isDone()) {
                AssertionError error = f.get();
                if (error != null) {
                    throw error;
                }
                else {
                    return false;
                }
            }
            else {
                return true;
            }
        }
        catch (InterruptedException ex) {
            throw new IllegalStateException("Interrupted", ex);
        }
        catch (ExecutionException ex) {
            throw new IllegalStateException("Failed future", ex.getCause());
        }
    }

    private void timeOut(CountDownLatch l)
    {
        try {
            boolean rc = l.await(1, TimeUnit.SECONDS);
            assertTrue(rc);
        }
        catch (InterruptedException ex) {
            throw new IllegalStateException("Interrupted", ex);
        }
    }

    @Test(timeout = 1000)
    public void testCustomMonitor() throws InterruptedException
    {
        String addr = "tcp://127.0.0.1:*";
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        CountDownLatch listeningLatch = new CountDownLatch(1);
        CountDownLatch closedLatch = new CountDownLatch(1);
        CountDownLatch stoppedLatch = new CountDownLatch(1);
        boolean rc = rep.setEventHook(e -> {
            switch (e.event) {
            case ZMQ.ZMQ_EVENT_LISTENING:
                assertThat(Thread.currentThread().getName(), is("Time-limited test"));
                listeningLatch.countDown();
                break;
            case ZMQ.ZMQ_EVENT_CLOSED:
                assertThat(Thread.currentThread().getName(), is("iothread-2"));
                closedLatch.countDown();
                break;
            case ZMQ.ZMQ_EVENT_MONITOR_STOPPED:
                assertThat(Thread.currentThread().getName(), is("reaper-1"));
                stoppedLatch.countDown();
                break;
            default:
                fail();
            }
        }, ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));
        rc = ZMQ.bind(rep, addr);
        assertThat(rc, is(true));
        rc = listeningLatch.await(1, TimeUnit.SECONDS);
        assertThat(rc, is(true));
        rep.close();
        rc = closedLatch.await(1, TimeUnit.SECONDS);
        assertThat(rc, is(true));
        rc = stoppedLatch.await(1, TimeUnit.SECONDS);
        assertThat(rc, is(true));
        ctx.terminate();
    }

    @Test(timeout = 1000)
    public void testDisableMonitor()
    {
        String addr = "tcp://127.0.0.1:*";
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        boolean rc = rep.setEventHook(e -> {
            assertThat(e.event, is(ZMQ.ZMQ_EVENT_MONITOR_STOPPED));
            assertThat(Thread.currentThread().getName(), is("Time-limited test"));
        }, ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));
        rc = rep.monitor(null, ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));
        rc = ZMQ.bind(rep, addr);
        assertThat(rc, is(true));
        rep.close();
        ctx.terminate();
    }
}
