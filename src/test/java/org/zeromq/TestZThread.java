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

import org.zeromq.ZMQ.Socket;
import org.junit.Assert;
import org.junit.Test;

public class TestZThread
{
    @Test
    public void testDetached()
    {
        ZThread.IDetachedRunnable detached = new ZThread.IDetachedRunnable()
        {
            @Override
            public void run(Object[] args)
            {
                ZContext ctx = new ZContext();
                assert (ctx != null);

                Socket push = ctx.createSocket(ZMQ.PUSH);
                assert (push != null);
                ctx.destroy();
            }
        };

        ZThread.start(detached);
    }

    @Test
    public void testFork()
    {
        ZContext ctx = new ZContext();

        ZThread.IAttachedRunnable attached = new ZThread.IAttachedRunnable()
        {
            @Override
            public void run(Object[] args, ZContext ctx, Socket pipe)
            {
                //  Create a socket to check it'll be automatically deleted
                ctx.createSocket(ZMQ.PUSH);
                pipe.recvStr();
                pipe.send("pong");
            }
        };

        Socket pipe = ZThread.fork(ctx, attached);
        assert (pipe != null);

        pipe.send("ping");
        String pong = pipe.recvStr();

        Assert.assertEquals(pong, "pong");

        //  Everything should be cleanly closed now
        ctx.destroy();
    }
}
