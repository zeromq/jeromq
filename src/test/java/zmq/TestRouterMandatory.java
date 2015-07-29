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

public class TestRouterMandatory
{
    @Test
    public void testRouterMandatory() throws Exception
    {
        int rc;
        boolean brc;

        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sa = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        ZMQ.setSocketOption(sa, ZMQ.ZMQ_SNDHWM, 1);
        assertThat(sa, notNullValue());

        brc = ZMQ.bind(sa, "tcp://127.0.0.1:15560");
        assertThat(brc , is(true));

        // Sending a message to an unknown peer with the default setting
        rc = ZMQ.send(sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(7));
        rc = ZMQ.send(sa, "DATA", 0);
        assertThat(rc, is(4));

        int mandatory = 1;

        // Set mandatory routing on socket
        ZMQ.setSocketOption(sa, ZMQ.ZMQ_ROUTER_MANDATORY, mandatory);

        // Send a message and check that it fails
        rc = ZMQ.send(sa, "UNKNOWN", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        assertThat(rc, is(-1));
        assertThat(sa.errno(), is(ZError.EHOSTUNREACH));

        // Create a valid socket
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(sb, notNullValue());

        ZMQ.setSocketOption(sb, ZMQ.ZMQ_RCVHWM, 1);
        ZMQ.setSocketOption(sb, ZMQ.ZMQ_IDENTITY, "X");

        brc = ZMQ.connect(sb, "tcp://127.0.0.1:15560");

        // wait until connect
        Thread.sleep(1000);

        // make it full and check that it fails
        rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.send(sa, "DATA1", 0);
        assertThat(rc, is(5));

        rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        if (rc == 1) {
            // the first frame has been sent
            rc = ZMQ.send(sa, "DATA2", 0);
            assertThat(rc, is(5));

            // send more
            rc = ZMQ.send(sa, "X", ZMQ.ZMQ_SNDMORE | ZMQ.ZMQ_DONTWAIT);
        }
        assertThat(rc, is(-1));
        assertThat(sa.errno(), is(ZError.EAGAIN));

        //  Clean up.
        ZMQ.close(sa);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
