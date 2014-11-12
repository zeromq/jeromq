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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class TestRouterHandover
{
    @Test
    public void testRouterHandover() throws Exception
    {
        int rc;
        boolean brc;

        Ctx ctx = ZMQ.zmqInit(1);
        assertThat(ctx, notNullValue());

        SocketBase router = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_ROUTER);
        brc = ZMQ.zmq_bind(router, "tcp://127.0.0.1:15561");
        assertThat(brc , is(true));

        // Enable the handover flag
        ZMQ.zmq_setsockopt(router, ZMQ.ZMQ_ROUTER_HANDOVER, 1);
        assertThat(router, notNullValue());

        // Create dealer called "X" and connect it to our router
        SocketBase dealerOne = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealerOne, notNullValue());

        ZMQ.zmq_setsockopt(dealerOne, ZMQ.ZMQ_IDENTITY, "X");

        brc = ZMQ.zmq_connect(dealerOne, "tcp://127.0.0.1:15561");
        assertThat(brc, is(true));

        // Get message from dealer to know when connection is ready
        rc = ZMQ.zmq_send(dealerOne, "Hello", 0);
        assertThat(rc, is(5));

        Msg msg = ZMQ.zmq_recv(router, 0);
        assertThat(msg.size() , is(1));
        assertThat(new String(msg.data()) , is("X"));

        msg = ZMQ.zmq_recv(router, 0);
        assertThat(msg.size(), is(5));

        // Now create a second dealer that uses the same identity
        SocketBase dealerTwo = ZMQ.zmq_socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealerTwo, notNullValue());

        ZMQ.zmq_setsockopt(dealerTwo, ZMQ.ZMQ_IDENTITY, "X");

        brc = ZMQ.zmq_connect(dealerTwo, "tcp://127.0.0.1:15561");
        assertThat(brc, is(true));

        // Get message from dealer to know when connection is ready
        rc = ZMQ.zmq_send(dealerTwo, "Hello", 0);
        assertThat(rc, is(5));

        msg = ZMQ.zmq_recv(router, 0);
        assertThat(msg.size() , is(1));
        assertThat(new String(msg.data()) , is("X"));

        msg = ZMQ.zmq_recv(router, 0);
        assertThat(msg.size(), is(5));

        // Send a message to 'X' identity. This should be delivered
        // to the second dealer, instead of the first because of the handover.
        rc = ZMQ.zmq_send(router, "X", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.zmq_send(router, "Hello", 0);
        assertThat(rc, is(5));

        // Ensure that the first dealer doesn't receive the message
        // but the second one does
        msg = ZMQ.zmq_recv(dealerOne, ZMQ.ZMQ_DONTWAIT);
        assertThat(msg, nullValue());

        msg = ZMQ.zmq_recv(dealerTwo, 0);
        assertThat(msg.size(), is(5));

        //  Clean up.
        ZMQ.zmq_close(router);
        ZMQ.zmq_close(dealerOne);
        ZMQ.zmq_close(dealerTwo);
        ZMQ.zmq_term(ctx);
    }
}
