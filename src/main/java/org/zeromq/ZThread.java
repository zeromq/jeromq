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

public class ZThread
{
    private ZThread()
    {
    }

    public static interface IAttachedRunnable
    {
        public void run(Object[] args, ZContext ctx, Socket pipe);
    }

    public static interface IDetachedRunnable
    {
        public void run(Object[] args);
    }

    private static class ShimThread extends Thread
    {
        private ZContext ctx;
        private IAttachedRunnable attachedRunnable;
        private IDetachedRunnable detachedRunnable;
        private Object[] args;
        private Socket pipe;

        protected ShimThread(ZContext ctx, IAttachedRunnable runnable, Object [] args, Socket pipe)
        {
            assert (ctx != null);
            assert (pipe != null);
            assert (runnable != null);

            this.ctx = ctx;
            this.attachedRunnable = runnable;
            this.args = args;
            this.pipe = pipe;
        }

        public ShimThread(IDetachedRunnable runnable, Object[] args)
        {
            assert (runnable != null);
            this.detachedRunnable = runnable;
            this.args = args;
        }

        @Override
        public void run()
        {
            if (attachedRunnable != null) {
                attachedRunnable.run(args, ctx, pipe);
                ctx.destroy();
            }
            else {
                detachedRunnable.run(args);
            }
        }
    }

    //  --------------------------------------------------------------------------
    //  Create a detached thread. A detached thread operates autonomously
    //  and is used to simulate a separate process. It gets no ctx, and no
    //  pipe.

    public static void start(IDetachedRunnable runnable, Object ... args)
    {
        //  Prepare child thread
        Thread shim = new ShimThread(runnable, args);
        shim.start();
    }

    //  --------------------------------------------------------------------------
    //  Create an attached thread. An attached thread gets a ctx and a PAIR
    //  pipe back to its parent. It must monitor its pipe, and exit if the
    //  pipe becomes unreadable. Returns pipe, or null if there was an error.

    public static Socket fork(ZContext ctx, IAttachedRunnable runnable, Object ... args)
    {
        Socket pipe = ctx.createSocket(ZMQ.PAIR);

        if (pipe != null) {
            pipe.bind(String.format("inproc://zctx-pipe-%d", pipe.hashCode()));
        }
        else {
            return null;
        }

        //  Connect child pipe to our pipe
        ZContext ccontext = ZContext.shadow(ctx);
        Socket cpipe = ccontext.createSocket(ZMQ.PAIR);
        if (cpipe == null) {
            return null;
        }
        cpipe.connect(String.format("inproc://zctx-pipe-%d", pipe.hashCode()));

        //  Prepare child thread
        Thread shim = new ShimThread(ccontext, runnable, args, cpipe);
        shim.start();

        return pipe;
    }
}
