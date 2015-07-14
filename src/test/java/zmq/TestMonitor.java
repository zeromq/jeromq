/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package zmq;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestMonitor
{
    static class SocketMonitor extends Thread
    {
        private Ctx ctx;
        private int events;
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
                    events |= ZMQ.ZMQ_EVENT_LISTENING;
                    break;
                case ZMQ.ZMQ_EVENT_ACCEPTED:
                    events |= ZMQ.ZMQ_EVENT_ACCEPTED;
                    break;
                // connecter specific
                case ZMQ.ZMQ_EVENT_CONNECTED:
                    events |= ZMQ.ZMQ_EVENT_CONNECTED;
                    break;
                case ZMQ.ZMQ_EVENT_CONNECT_DELAYED:
                    events |= ZMQ.ZMQ_EVENT_CONNECT_DELAYED;
                    break;
                // generic - either end of the socket
                case ZMQ.ZMQ_EVENT_CLOSE_FAILED:
                    events |= ZMQ.ZMQ_EVENT_CLOSE_FAILED;
                    break;
                case ZMQ.ZMQ_EVENT_CLOSED:
                    events |= ZMQ.ZMQ_EVENT_CLOSED;
                    break;
                case ZMQ.ZMQ_EVENT_DISCONNECTED:
                    events |= ZMQ.ZMQ_EVENT_DISCONNECTED;
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
        String addr = "tcp://127.0.0.1:5590";
        SocketMonitor [] threads = new SocketMonitor [3];
        //  Create the infrastructure
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        // set socket monitor
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());
        try {
            ZMQ.monitorSocket(rep, addr, 0);
            assertTrue(false);
        }
        catch (IllegalArgumentException e) {
        }

        // REP socket monitor, all events
        boolean rc = ZMQ.monitorSocket(rep, "inproc://monitor.rep", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        threads [0] = new SocketMonitor(ctx, "inproc://monitor.rep");
        threads [0].start();

        rc = ZMQ.bind(rep, addr);
        assertThat(rc, is(true));

        SocketBase req = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());

        // REQ socket monitor, all events
        rc = ZMQ.monitorSocket(req, "inproc://monitor.req", ZMQ.ZMQ_EVENT_ALL);
        assertThat(rc, is(true));

        threads [1] = new SocketMonitor(ctx, "inproc://monitor.req");
        threads [1].start();

        rc = ZMQ.connect(req, addr);
        assertThat(rc, is(true));

        // 2nd REQ socket
        SocketBase req2 = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req2, notNullValue());

        // 2nd REQ socket monitor, connected event only
        rc = ZMQ.monitorSocket(req2, "inproc://monitor.req2", ZMQ.ZMQ_EVENT_CONNECTED);
        assertThat(rc, is(true));

        threads [2] = new SocketMonitor(ctx, "inproc://monitor.req2");
        threads [2].start();

        rc = ZMQ.connect(req2, addr);
        assertThat(rc, is(true));

        Helper.bounce(rep, req);

        // Allow a window for socket events as connect can be async
        ZMQ.sleep(1);

        // Close the REP socket
        ZMQ.close(rep);

        // Allow some time for detecting error states
        ZMQ.sleep(1);
        // Close the REQ socket
        ZMQ.close(req);
        // Close the 2nd REQ socket
        ZMQ.close(req2);

        // Allow for closed or disconnected events to bubble up
        ZMQ.sleep(1);

        ZMQ.term(ctx);

        // Expected REP socket events
        // We expect to at least observe these events
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_LISTENING)  > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_ACCEPTED) > 0);
        assertTrue((threads[0].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);

        // Expected REQ socket events
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CONNECTED)  > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_DISCONNECTED) > 0);
        assertTrue((threads[1].events & ZMQ.ZMQ_EVENT_CLOSED) > 0);

        // Expected 2nd REQ socket events
        assertTrue((threads[2].events & ZMQ.ZMQ_EVENT_CONNECTED) > 0);
        assertTrue((threads[2].events & ZMQ.ZMQ_EVENT_CLOSED) == 0);

        threads[0].join();
        threads[1].join();
        threads[2].join();
    }
}
