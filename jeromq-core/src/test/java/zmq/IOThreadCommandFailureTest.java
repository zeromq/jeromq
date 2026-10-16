package zmq;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import zmq.io.IOThread;
import zmq.socket.Sockets;

// https://github.com/zeromq/jeromq/issues/905
public class IOThreadCommandFailureTest
{
    private static class FailingPlug extends ZObject
    {
        FailingPlug(IOThread ioThread)
        {
            super(ioThread);
        }

        @Override
        protected void processPlug()
        {
            throw new IllegalStateException("command failure");
        }
    }

    private static class CountingPlug extends ZObject
    {
        private final CountDownLatch processed;

        CountingPlug(IOThread ioThread, CountDownLatch processed)
        {
            super(ioThread);
            this.processed = processed;
        }

        @Override
        protected void processPlug()
        {
            processed.countDown();
        }

        @Override
        protected void processSeqnum()
        {
            // Not an Own, nothing to track
        }
    }

    @Test(timeout = 10000)
    public void testCommandsAreProcessedAfterACommandFails() throws InterruptedException
    {
        Ctx ctx = ZMQ.init(1);
        AtomicReference<Throwable> notified = new AtomicReference<>();
        ctx.setNotificationExceptionHandler((t, e) -> notified.compareAndSet(null, e));

        // Creating a socket starts the I/O threads
        SocketBase socket = ZMQ.socket(ctx, Sockets.PAIR.ordinal());
        IOThread ioThread = ctx.chooseIoThread(0);

        CountDownLatch processed = new CountDownLatch(1);
        ioThread.getMailbox().send(new Command(new FailingPlug(ioThread), Command.Type.PLUG));
        ioThread.getMailbox().send(new Command(new CountingPlug(ioThread, processed), Command.Type.PLUG));

        boolean rc = processed.await(5, TimeUnit.SECONDS);
        assertThat("command after the failing one was not processed", rc, is(true));
        assertThat(notified.get(), instanceOf(IllegalStateException.class));

        ZMQ.close(socket);
        ZMQ.term(ctx);
    }
}
