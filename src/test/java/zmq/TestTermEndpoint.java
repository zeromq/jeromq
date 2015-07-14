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
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestTermEndpoint
{
    @Test
    public void testTermEndpoint() throws Exception
    {
        String ep = "tcp://127.0.0.1:7590";
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        boolean rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        SocketBase pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        int r = ZMQ.send(push, "ABC",  0);
        assertThat(r, is(3));
        Msg msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Unbind the lisnening endpoint
        rc = ZMQ.unbind(push, ep);
        assertThat(rc, is(true));

        // Let events some time
        ZMQ.sleep(1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.send(push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assertThat(r, is(-1));

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);

        //  Now the other way round.
        System.out.println("disconnect endpoint test running...");

        ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH);
        assertThat(push, notNullValue());
        rc = ZMQ.bind(push, ep);
        assertThat(rc, is(true));

        pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        assertThat(pull, notNullValue());
        rc = ZMQ.connect(pull, ep);
        assertThat(rc, is(true));

        //  Pass one message through to ensure the connection is established.
        r = ZMQ.send(push, "ABC",  0);
        assertThat(r, is(3));
        msg = ZMQ.recv(pull, 0);
        assertThat(msg.size(), is(3));

        // Disconnect the bound endpoint
        rc = ZMQ.disconnect(push, ep);
        assertThat(rc, is(true));

        // Let events some time
        ZMQ.sleep(1);

        //  Check that sending would block (there's no outbound connection).
        r = ZMQ.send(push, "ABC",  ZMQ.ZMQ_DONTWAIT);
        assertThat(r, is(-1));

        //  Clean up.
        ZMQ.close(pull);
        ZMQ.close(push);
        ZMQ.term(ctx);
    }
}
