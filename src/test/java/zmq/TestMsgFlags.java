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

public class TestMsgFlags
{
    //  Create REQ/ROUTER wiring.

    @Test
    public void testMsgFlags()
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());
        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_ROUTER);
        assertThat(sb, notNullValue());
        boolean brc = ZMQ.bind(sb, "inproc://a");
        assertThat(brc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        assertThat(sc, notNullValue());
        brc = ZMQ.connect(sc, "inproc://a");
        assertThat(brc, is(true));

        int rc;
        //  Send 2-part message.
        rc = ZMQ.send(sc, "A", ZMQ.ZMQ_SNDMORE);
        assertThat(rc, is(1));
        rc = ZMQ.send(sc, "B", 0);
        assertThat(rc, is(1));

        //  Identity comes first.
        Msg msg = ZMQ.recvMsg(sb, 0);
        int more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(1));

        //  Then the first part of the message body.
        msg = ZMQ.recvMsg(sb, 0);
        assertThat(rc, is(1));
        more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(1));

        //  And finally, the second part of the message body.
        msg = ZMQ.recvMsg(sb, 0);
        assertThat(rc, is(1));
        more = ZMQ.getMessageOption(msg, ZMQ.ZMQ_MORE);
        assertThat(more, is(0));

        //  Tear down the wiring.
        ZMQ.close(sb);
        ZMQ.close(sc);
        ZMQ.term(ctx);
    }
}
