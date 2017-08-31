package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZThread.IAttachedRunnable;

import zmq.ZError;

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

                Socket push = ctx.createSocket(ZMQ.PUSH);
                assertThat(push, notNullValue());

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
        final ZContext ctx = new ZContext();

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
        assertThat(pipe, notNullValue());

        pipe.send("ping");
        String pong = pipe.recvStr();

        assertThat(pong, is("pong"));

        //  Everything should be cleanly closed now
        ctx.close();
    }

    @Test
    @Ignore
    public void testClosePipe()
    {
        ZContext ctx = new ZContext();
        IAttachedRunnable runnable = new IAttachedRunnable()
        {
            @Override
            public void run(Object[] args, ZContext ctx, Socket pipe)
            {
                pipe.recvStr();
                pipe.send("pong");
            }
        };
        Socket pipe = ZThread.fork(ctx, runnable);

        assertThat(pipe, notNullValue());

        boolean rc = pipe.send("ping");
        assertThat(rc, is(true));

        String pong = pipe.recvStr();

        assertThat(pong, is("pong"));

        try {
            rc = pipe.send("boom ?!");
            assertThat("pipe was closed pretty fast", rc, is(true));
        }
        catch (ZMQException e) {
            int errno = e.getErrorCode();
            assertThat("Expected exception has the wrong code", ZError.ETERM, is(errno));
        }

        ctx.close();
    }
}
