package zmq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import zmq.util.Utils;

public class TestMonitor
{
    static class SocketMonitor extends Thread
    {
        private Ctx    ctx;
        private int    events;
        private String monitorAddr;

        public SocketMonitor(Ctx ctx, String monitorAddr)
        {
            this.ctx = ctx;
            this.monitorAddr = monitorAddr;
            events = 0;
        }

        @Override
        public void run()
        {
            SocketBase s = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            boolean rc = s.connect(monitorAddr);
            assertThat(rc, is(true));
            // Only some of the exceptional events could fire
            while (true) {
                ZMQ.Event event = ZMQ.Event.read(s);
                if (event == null && s.errno() == ZError.ETERM) {
                    break;
                }
                assertThat(event, notNullValue());

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
                    events |= event.event;
                    break;
                default:
                    // out of band / unexpected event
                    assertTrue("Unkown Event " + event.event, true);
                }
            }
            s.close();
        }
    }

    @Test
    public void testMonitor() throws Exception
    {
        int port = Utils.findOpenPort();
        String addr = "tcp://127.0.0.1:" + port;
        SocketMonitor[] threads = new SocketMonitor[3];
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        // set socket monitor
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        boolean rc = ZMQ.monitorSocket(rep, addr, 0);
        assertThat(rc, is(false));
        assertThat(rep.errno.get(), is(ZError.EPROTONOSUPPORT));

        // REP socket monitor, all events
        rc = ZMQ.monitorSocket(rep, "inproc://monitor.rep", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        threads[0] = new SocketMonitor(ctx, "inproc://monitor.rep");
        threads[0].start();
        threads[1] = new SocketMonitor(ctx, "inproc://monitor.req");
        threads[1].start();
        threads[2] = new SocketMonitor(ctx, "inproc://monitor.req2");
        threads[2].start();

        ZMQ.sleep(1);
        rc = ZMQ.bind(rep, addr);
        assertThat(rc, is(true));

        SocketBase req = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());

        // REQ socket monitor, all events
        rc = ZMQ.monitorSocket(req, "inproc://monitor.req", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        rc = ZMQ.connect(req, addr);
        assertThat(rc, is(true));

        // 2nd REQ socket
        SocketBase req2 = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req2, notNullValue());

        // 2nd REQ socket monitor, connected event only
        rc = ZMQ.monitorSocket(req2, "inproc://monitor.req2", ZMQ.ZMQ_EVENT_CONNECTED);
        assertThat(rc, is(true));

        rc = ZMQ.connect(req2, addr);
        assertThat(rc, is(true));

        Helper.bounce(rep, req);

        // Allow a window for socket events as connect can be async
        ZMQ.sleep(1);

        ZMQ.setSocketOption(rep, ZMQ.ZMQ_LINGER, 0);
        // Close the REP socket
        ZMQ.close(rep);

        // Allow some time for detecting error states
        ZMQ.sleep(1);

        ZMQ.setSocketOption(req, ZMQ.ZMQ_LINGER, 0);
        // Close the REQ socket
        ZMQ.close(req);
        ZMQ.setSocketOption(req2, ZMQ.ZMQ_LINGER, 0);
        // Close the 2nd REQ socket
        ZMQ.close(req2);

        // Allow for closed or disconnected events to bubble up
        ZMQ.sleep(1);

        ZMQ.term(ctx);

        // Expected REP socket events
        // We expect to at least observe these events
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_LISTENING) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_ACCEPTED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) > 0);

        // Expected REQ socket events
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CONNECT_DELAYED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CONNECTED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_MONITOR_STOPPED) > 0);

        // Expected 2nd REQ socket events
        assertTrue(threads[2].events == ZMQ.ZMQ_EVENT_CONNECTED);

        threads[0].join();
        threads[1].join();
        threads[2].join();
    }
}
