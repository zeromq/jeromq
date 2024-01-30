package zmq.poll;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class PollerBaseTest
{
    private final IPollEvents sink = new PollEvents();

    @Test
    public void testNoTimer()
    {
        PollerBase poller = new PollerBaseTested();

        long timeout = poller.executeTimers();
        assertThat(timeout, is(0L));

        poller.addTimer(1000, sink, 1);
    }

    @Test
    public void testOneTimer()
    {
        final PollerBaseTested poller = new PollerBaseTested();

        poller.addTimer(1000, sink, 1);
        long timeout = poller.executeTimers();

        assertThat(timeout, is(1000L));
        assertThat(poller.isEmpty(), is(false));

        poller.clock(200);
        timeout = poller.executeTimers();

        assertThat(timeout, is(800L));
        assertThat(poller.isEmpty(), is(false));

        poller.clock(1000);
        timeout = poller.executeTimers();

        assertThat(poller.isEmpty(), is(true));
        assertThat(timeout, is(0L));
    }

    @Test
    public void testCancelTimer()
    {
        final PollerBaseTested poller = new PollerBaseTested();

        poller.addTimer(1000, sink, 1);
        long timeout = poller.executeTimers();

        assertThat(timeout, is(1000L));
        assertThat(poller.isEmpty(), is(false));

        poller.cancelTimer(sink, 1);
        timeout = poller.executeTimers();

        assertThat(timeout, is(0L));
        assertThat(poller.isEmpty(), is(true));
    }

    @Test
    public void testCancelTimerInTimerEvent()
    {
        final PollerBaseTested poller = new PollerBaseTested();

        PollEvents sink = new PollEvents()
        {
            @Override
            public void timerEvent(int id)
            {
                // cancelTimer() is never called in timerEvent()
                poller.cancelTimer(this, id);
            }
        };
        poller.addTimer(1000, sink, 1);

        poller.clock(1000);
        long rc = poller.executeTimers();
        assertThat(rc, is(0L));
        assertThat(poller.isEmpty(), is(true));
    }

    @Test
    public void testAddTimerInTimerEvent()
    {
        final int id = 1;
        final PollerBaseTested poller = new PollerBaseTested();

        final AtomicInteger counter = new AtomicInteger();
        PollEvents sink = new PollEvents()
        {
            @Override
            public void timerEvent(int id)
            {
                counter.incrementAndGet();
                poller.addTimer(100, this, id);
            }
        };

        poller.addTimer(1000, sink, id);
        assertThat(poller.isEmpty(), is(false));

        poller.clock(1000);
        long timeout = poller.executeTimers();

        assertThat(counter.get(), is(1));
        assertThat(poller.isEmpty(), is(false));
        assertThat(timeout, is(100L));

        poller.cancelTimer(sink, id);
        poller.clock(3000);
        timeout = poller.executeTimers();

        assertThat(counter.get(), is(1));
        assertThat(timeout, is(0L));
        assertThat(poller.isEmpty(), is(true));
    }

    @Test
    public void testAddTimer()
    {
        final PollerBaseTested poller = new PollerBaseTested();

        poller.addTimer(1000, new PollEvents()
        {
            private boolean first = true;

            @Override
            public void timerEvent(int id)
            {
                if (first) {
                    // expires at 2000 + 1000
                    poller.addTimer(2000, this, id);
                }
                first = false;
            }
        }, 1);
        poller.clock(1000);
        long timeout = poller.executeTimers();

        assertThat(poller.isEmpty(), is(false));
        assertThat(timeout, is(2000L));

        poller.clock(3000);
        timeout = poller.executeTimers();

        assertThat(timeout, is(0L));
        assertThat(poller.isEmpty(), is(true));
    }
}
