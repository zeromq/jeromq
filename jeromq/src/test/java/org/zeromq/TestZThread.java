package org.zeromq;

import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestZThread
{
    @Test
    public void testDetached()
    {
        final CountDownLatch stopped = new CountDownLatch(1);
        ZThread.start((args) -> {
            try (ZContext ctx = new ZContext()) {
                Socket push = ctx.createSocket(SocketType.PUSH);
                assertThat(push, notNullValue());
            }
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

        Socket pipe = ZThread.fork(ctx, (args, ctx1, pipe1) -> {
            //  Create a socket to check it'll be automatically deleted
            ctx1.createSocket(SocketType.PUSH);
            pipe1.recvStr();
            pipe1.send("pong");
        });
        assertThat(pipe, notNullValue());

        pipe.send("ping");
        String pong = pipe.recvStr();

        assertThat(pong, is("pong"));

        //  Everything should be cleanly closed now
        ctx.close();
    }

    @Test(timeout = 1000)
    public void testCriticalException() throws InterruptedException
    {
        final CountDownLatch stopped = new CountDownLatch(1);
        try (final ZContext ctx = new ZContext()) {
            ctx.setUncaughtExceptionHandler((t, e) -> stopped.countDown());
            Socket pipe = ZThread.fork(ctx, (args, ctx1, pipe1) -> {
                throw new Error("critical");
            });
            assertThat(pipe, notNullValue());
            stopped.await();
        }
    }

    @Test
    @Ignore
    public void testClosePipe()
    {
        ZContext ctx = new ZContext();
        Socket pipe = ZThread.fork(ctx, (args, ctx1, pipe1) -> {
            pipe1.recvStr();
            pipe1.send("pong");
        });

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
