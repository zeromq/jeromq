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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestSubForward
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testSubForward()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase xpub = ZMQ.socket(ctx, ZMQ.ZMQ_XPUB);
        assertThat(xpub, notNullValue());
        boolean rc = ZMQ.bind(xpub, "tcp://127.0.0.1:5570");

        SocketBase xsub = ZMQ.socket(ctx, ZMQ.ZMQ_XSUB);
        assertThat(xsub, notNullValue());
        rc = ZMQ.bind(xsub, "tcp://127.0.0.1:5571");
        assertThat(rc, is(true));

        SocketBase pub = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(pub, notNullValue());
        rc = ZMQ.connect(pub, "tcp://127.0.0.1:5571");
        assertThat(rc, is(true));

        SocketBase sub = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(sub, notNullValue());
        rc = ZMQ.connect(sub, "tcp://127.0.0.1:5570");
        assertThat(rc, is(true));

        ZMQ.setSocketOption(sub, ZMQ.ZMQ_SUBSCRIBE, "");
        Msg msg = ZMQ.recv(xpub, 0);
        assertThat(msg, notNullValue());
        int n = ZMQ.send(xsub, msg, 0);
        assertThat(n, not(0));

        ZMQ.sleep(1);

        n = ZMQ.send(pub, null, 0, 0);
        assertThat(n, is(0));

        msg = ZMQ.recv(xsub, 0);
        assertThat(msg, notNullValue());

        n = ZMQ.send(xpub, msg, 0);
        assertThat(n, is(0));

        msg = ZMQ.recv(sub, 0);
        assertThat(msg, notNullValue());

        //  Tear down the wiring.
        ZMQ.close(xpub);
        ZMQ.close(xsub);
        ZMQ.close(pub);
        ZMQ.close(sub);
        ZMQ.term(ctx);
    }
}
