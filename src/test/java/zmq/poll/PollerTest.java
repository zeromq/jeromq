package zmq.poll;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
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
}
