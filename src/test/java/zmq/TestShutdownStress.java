/*
    Copyright (c) 2010-2011 250bpm s.r.o.
    Copyright (c) 2011 iMatix Corporation
    Copyright (c) 2010-2011 Other contributors as noted in the AUTHORS file

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
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestShutdownStress
{
    private static final int THREAD_COUNT = 100;

    class Worker implements Runnable
    {
        SocketBase s;
        Worker(SocketBase s)
        {
            this.s = s;
        }

        @Override
        public void run()
        {
            boolean rc = ZMQ.zmq_connect(s, "tcp://127.0.0.1:5560");
            assertThat(rc, is(true));

            //  Start closing the socket while the connecting process is underway.
            ZMQ.zmq_close(s);
        }
    }

    @Test
    public void testShutdownStress() throws Exception
    {
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int j = 0; j != 10; j++) {
            Ctx ctx = ZMQ.zmq_init(7);
            assertThat(ctx, notNullValue());

            SocketBase s1 = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_PUB);
            assertThat(s1, notNullValue());

            boolean rc = ZMQ.zmq_bind(s1, "tcp://127.0.0.1:7570");
            assertThat(rc, is(true));

            for (int i = 0; i != THREAD_COUNT; i++) {
                SocketBase s2 = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_SUB);
                assert (s2 != null);
                threads[i] = new Thread(new Worker(s2));
                threads[i].start();
            }

            for (int i = 0; i != THREAD_COUNT; i++) {
                threads[i].join();
            }

            ZMQ.zmq_close(s1);
            ZMQ.zmq_term(ctx);
        }
    }
}
