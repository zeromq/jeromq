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
import org.zeromq.ZMQ.Socket;

public class TestZContext
{
    @Test
    public void testZContext()
    {
        ZContext ctx = new ZContext();
        Socket s1 = ctx.createSocket(ZMQ.PAIR);
        Socket s2 = ctx.createSocket(ZMQ.XREQ);
        Socket s3 = ctx.createSocket(ZMQ.REQ);
        Socket s4 = ctx.createSocket(ZMQ.REP);
        Socket s5 = ctx.createSocket(ZMQ.PUB);
        Socket s6 = ctx.createSocket(ZMQ.SUB);
        ctx.close();
        Assert.assertEquals(0, ctx.getSockets().size());
    }

    @Test
    public void testZContextSocketCloseBeforeContextClose()
    {
        ZContext ctx = new ZContext();
        Socket s1 = ctx.createSocket(ZMQ.PUSH);
        Socket s2 = ctx.createSocket(ZMQ.PULL);
        s1.close();
        s2.close();
        ctx.close();
    }
}
