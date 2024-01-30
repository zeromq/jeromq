package zmq.poll;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import zmq.Ctx;

public class PollerTest
{
    @Test(timeout = 1000)
    public void catchNotification() throws IOException, InterruptedException
    {
        CountDownLatch catched = new CountDownLatch(1);
        AtomicReference<Selector> selectorRef = new AtomicReference<>();
        Ctx ctx = new Ctx()
        {
            @Override
            public Selector createSelector()
            {
                selectorRef.set(super.createSelector());
                return selectorRef.get();
            }
        };
        ctx.setNotificationExceptionHandler((t, e) ->
        {
            if (e instanceof ClosedSelectorException) {
                catched.countDown();
            }
        });
        Poller poller = new Poller(ctx, "test");
        poller.start();
        selectorRef.get().close();
        catched.await();
    }

    @Test(timeout = 1000)
    public void catchFailure() throws IOException, InterruptedException
    {
        CountDownLatch catched = new CountDownLatch(1);
        Ctx ctx = new Ctx();
        ctx.setUncaughtExceptionHandler((t, e) ->
        {
            if (e instanceof OutOfMemoryError) {
                catched.countDown();
            }
        });
        Poller poller = new Poller(ctx, "test");
        Pipe signaler = Pipe.open();
        try (Pipe.SourceChannel source = signaler.source();
            Pipe.SinkChannel sink = signaler.sink()
        ) {
            source.configureBlocking(false);
            Poller.Handle handle = poller.addHandle(signaler.source(), new IPollEvents()
            {
                @Override
                public void inEvent()
                {
                    throw new OutOfMemoryError();
                }
            });
            poller.setPollIn(handle);
            poller.start();
            sink.write(ByteBuffer.allocate(1));
            catched.await();
        }
    }

    @Test(timeout = 1000)
    public void threadFactory() throws InterruptedException
    {
        CountDownLatch catched = new CountDownLatch(1);
        Ctx ctx = new Ctx();
        ctx.setThreadFactory((r, s) -> {
            catched.countDown();
            return new Thread(r, s);
        });
        new Poller(ctx, "test");
        catched.await();
    }
}
