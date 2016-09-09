package org.zeromq;

import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;

public class TestZThread
{
    @Test
    public void testDetached()
    {
        final CountDownLatch stopped = new CountDownLatch(1);
        ZThread.IDetachedRunnable detached = new ZThread.IDetachedRunnable()
        {
            @Override
            public void run(Object[] args)
            {
                ZContext ctx = new ZContext();
                assert (ctx != null);

                Socket push = ctx.createSocket(ZMQ.PUSH);
                assert (push != null);
                ctx.close();
                stopped.countDown();
            }
        };

        ZThread.start(detached);
        try {
            stopped.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
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
