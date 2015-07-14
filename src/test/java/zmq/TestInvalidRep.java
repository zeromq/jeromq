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

public class TestInvalidRep
{
    //  Create REQ/ROUTER wiring.
    @Test
    public void testInvalidRep()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase routerSocket = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(routerSocket, notNullValue());

        SocketBase reqSocket = ZMQ.socket(ctx, ZMQ.ZMQ_REQ);
        assertThat(reqSocket, notNullValue());
        int linger = 0;
        int rc;
        ZMQ.setSocketOption(routerSocket, ZMQ.ZMQ_LINGER, linger);
        ZMQ.setSocketOption(reqSocket, ZMQ.ZMQ_LINGER, linger);
        boolean brc = ZMQ.bind(routerSocket, "inproc://hi");
        assertThat(brc, is(true));
        brc = ZMQ.connect(reqSocket, "inproc://hi");
        assertThat(brc, is(true));

        //  Initial request.
        rc = ZMQ.send(reqSocket, "r", 0);
        assertThat(rc, is(1));

        //  Receive the request.
        Msg addr;
        Msg bottom;
        Msg body;
        addr = ZMQ.recv(routerSocket, 0);
        int addrSize = addr.size();
        System.out.println("addrSize: " + addr.size());
        assertThat(addr.size() > 0, is(true));
        bottom = ZMQ.recv(routerSocket,  0);
        assertThat(bottom.size(), is(0));
        body = ZMQ.recv(routerSocket,  0);
        assertThat(body.size(), is(1));
        assertThat(body.data()[0], is((byte) 'r'));

        //  Send invalid reply.
        rc = ZMQ.send(routerSocket, addr, 0);
        assertThat(rc, is(addrSize));
        //  Send valid reply.
        rc = ZMQ.send(routerSocket, addr, ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(addrSize));
        rc = ZMQ.send(routerSocket, bottom, ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(0));
        rc = ZMQ.send(routerSocket, "b", 0);
        assertThat(rc, is(1));

        //  Check whether we've got the valid reply.
        body = ZMQ.recv(reqSocket, 0);
        assertThat(body.size(), is(1));
        assertThat(body.data()[0] , is((byte) 'b'));

        //  Tear down the wiring.
        ZMQ.close(routerSocket);
        ZMQ.close(reqSocket);
        ZMQ.term(ctx);
    }
}
