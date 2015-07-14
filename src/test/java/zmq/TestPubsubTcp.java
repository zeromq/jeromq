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

public class TestPubsubTcp
{
    @Test
    public void testPubsubTcp() throws Exception
    {
        Ctx ctx = ZMQ.init(1);
        assertThat(ctx, notNullValue());

        SocketBase sb = ZMQ.socket(ctx, ZMQ.ZMQ_PUB);
        assertThat(sb, notNullValue());
        boolean rc = ZMQ.bind(sb, "tcp://127.0.0.1:7660");
        assertThat(rc, is(true));

        SocketBase sc = ZMQ.socket(ctx, ZMQ.ZMQ_SUB);
        assertThat(sc, notNullValue());

        sc.setSocketOpt(ZMQ.ZMQ_SUBSCRIBE, "topic");

        rc = ZMQ.connect(sc, "tcp://127.0.0.1:7660");
        assertThat(rc, is(true));

        ZMQ.sleep(2);

        sb.send(new Msg("topic abc".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topix defg".getBytes(ZMQ.CHARSET)), 0);
        sb.send(new Msg("topic defgh".getBytes(ZMQ.CHARSET)), 0);

        Msg msg = sc.recv(0);
        assertThat(msg.size(), is(9));

        msg = sc.recv(0);
        assertThat(msg.size(), is(11));

        ZMQ.close(sc);
        ZMQ.close(sb);
        ZMQ.term(ctx);
    }
}
