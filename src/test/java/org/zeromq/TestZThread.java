package org.zeromq;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZThread.IAttachedRunnable;
import zmq.ZError;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class TestZThread
{
    @Test
    public void testDetached()
    {
        final CountDownLatch stopped = new CountDownLatch(1);
        ZThread.start((args) -> {
            ZContext ctx = new ZContext();
            Socket push = ctx.createSocket(SocketType.PUSH);
            assertThat(push, notNullValue());

            ctx.close();
            stopped.countDown();
        });
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

        ZThread.IAttachedRunnable attached = (args, ctx1, pipe) -> {
            //  Create a socket to check it'll be automatically deleted
            ctx1.createSocket(SocketType.PUSH);
            pipe.recvStr();
            pipe.send("pong");
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
        IAttachedRunnable runnable = (args, ctx1, pipe) -> {
            pipe.recvStr();
            pipe.send("pong");
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
