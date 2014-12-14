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

package org.zeromq;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by hartmann on 3/21/14.
 */
public class ZMsgTest
{
    @Test
    public void testRecvFrame() throws Exception
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket  socket   = ctx.socket(ZMQ.PULL);

        ZFrame f = ZFrame.recvFrame(socket, ZMQ.NOBLOCK);
        Assert.assertNull(f);

        socket.close();
        ctx.close();
    }

    @Test
    public void testRecvMsg() throws Exception
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket  socket   = ctx.socket(ZMQ.PULL);

        ZMsg msg = ZMsg.recvMsg(socket, ZMQ.NOBLOCK);
        Assert.assertNull(msg);

        socket.close();
        ctx.close();
    }

    @Test
    public void testRecvNullByteMsg() throws Exception
    {
        ZMQ.Context ctx = ZMQ.context(0);
        ZMQ.Socket sender = ctx.socket(ZMQ.PUSH);
        ZMQ.Socket receiver = ctx.socket(ZMQ.PULL);

        receiver.bind("inproc://" + this.hashCode());
        sender.connect("inproc://" + this.hashCode());

        sender.send(new byte[0]);
        ZMsg msg = ZMsg.recvMsg(receiver, ZMQ.NOBLOCK);
        Assert.assertNotNull(msg);

        sender.close();
        receiver.close();
        ctx.close();
    }
}
