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

public class TestReqrepDevice
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testReprepDevice()
    {
        boolean brc;
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        //  Create a req/rep device.
        SocketBase dealer = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(dealer, notNullValue());

        brc = ZMQ.bind(dealer, "tcp://127.0.0.1:5580");
        assertThat(brc , is(true));

        SocketBase router = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(router, notNullValue());

        brc = ZMQ.bind(router, "tcp://127.0.0.1:5581");
        assertThat(brc , is(true));

        //  Create a worker.
        SocketBase rep = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        assertThat(rep, notNullValue());

        brc = ZMQ.connect(rep, "tcp://127.0.0.1:5580");
        assertThat(brc , is(true));

        SocketBase req = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(req, notNullValue());

        brc = ZMQ.connect(req, "tcp://127.0.0.1:5581");
        assertThat(brc, is(true));

        //  Send a request.
        int rc;
        Msg msg;
        String buff;
        long rcvmore;

        rc = ZMQ.send(req, "ABC", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(3));
        rc = ZMQ.send(req, "DEFG", 0);
        assertThat(rc, is(4));

        //  Pass the request through the device.
        for (int i = 0; i != 4; i++) {
            msg = ZMQ.recvMsg(router, 0);
            assertThat(msg, notNullValue());
            rcvmore = ZMQ.getSocketOption(router, ZMQ.ZMQ_RCVMORE);
            rc = ZMQ.sendMsg(dealer, msg, rcvmore > 0 ? ZMQ.ZMQ_SNDMORE : 0);
            assertThat(rc >= 0, is(true));
        }

        //  Receive the request.
        msg = ZMQ.recv(rep, 0);
        assertThat(msg.size() , is(3));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat(buff , is("ABC"));
        rcvmore = ZMQ.getSocketOption(rep, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore > 0, is(true));
        msg = ZMQ.recv(rep, 0);
        assertThat(msg.size(), is(4));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat(buff, is("DEFG"));
        rcvmore = ZMQ.getSocketOption(rep, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(0L));

        //  Send the reply.
        rc = ZMQ.send(rep, "GHIJKL", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(6));
        rc = ZMQ.send(rep, "MN", 0);
        assertThat(rc , is(2));

        //  Pass the reply through the device.
        for (int i = 0; i != 4; i++) {
            msg = ZMQ.recvMsg(dealer, 0);
            assertThat(msg, notNullValue());
            rcvmore = ZMQ.getSocketOption(dealer, ZMQ.ZMQ_RCVMORE);
            rc = ZMQ.sendMsg(router, msg, rcvmore > 0 ? ZMQ.ZMQ_SNDMORE : 0);
            assertThat(rc >= 0, is(true));
        }

        //  Receive the reply.
        msg = ZMQ.recv(req, 0);
        assertThat(msg.size(), is(6));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat(buff, is("GHIJKL"));
        rcvmore = ZMQ.getSocketOption(req, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore > 0, is(true));
        msg = ZMQ.recv(req, 0);
        assertThat(msg.size(), is(2));
        buff = new String(msg.data(), ZMQ.CHARSET);
        assertThat(buff, is("MN"));
        rcvmore = ZMQ.getSocketOption(req, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore, is(0L));

        //  Clean up.
        ZMQ.close(req);
        ZMQ.close(rep);
        ZMQ.close(router);
        ZMQ.close(dealer);
        ZMQ.term(ctx);
    }
}
