package org.zeromq.timer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.zeromq.timer.ZTimer.Timer;

public class ZTimerTest
{
    private final AtomicLong    time = new AtomicLong();
    private final ZTimer        timers = new ZTimer(time::get);
    private AtomicBoolean invoked = new AtomicBoolean();

    private final TimerHandler handler = args -> {
        AtomicBoolean invoked = (AtomicBoolean) args[0];
        invoked.set(true);
    };

    private static final TimerHandler NOOP = args -> {
        // do nothing
    };

    @Before
    public void setup()
    {
        time.set(0);
        invoked = new AtomicBoolean();
    }

    @Test
    public void testAddFaultyHandler()
    {
        Timer timer = timers.add(10, null);
        assertThat(timer, nullValue());
    }

    @Test
    public void testCancelTwice()
    {
        Timer timer = timers.add(10, handler);
        assertThat(timer, notNullValue());

        boolean rc = timer.cancel();
        assertThat(rc, is(true));

        rc = timer.cancel();
        assertThat(rc, is(false));
    }

    @Test
    public void testTimeoutNoActiveTimers()
    {
        long timeout = timers.timeout();
        assertThat(timeout, is(-1L));
    }

    @Test
    public void testNotInvokedInitial()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);
        //  Timer should not have been invoked yet
        int rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testNotInvokedHalfTime()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);

        //  Wait half the time and check again
        long timeout = timers.timeout();
        time.set(time.get() + timeout / 2);
        int rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testInvoked()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);

        time.set(time.get() + fullTimeout);
        // Wait until the end
        timers.sleepAndExecute();
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testNotInvokedAfterHalfTimeAgain()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);

        time.set(time.get() + fullTimeout);
        // Wait until the end
        timers.sleepAndExecute();
        assertThat(invoked.get(), is(true));

        //  Wait half the time and check again
        long timeout = timers.timeout();
        time.set(time.get() + timeout / 2);
        int rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testNotInvokedAfterResetHalfTime()
    {
        assertNotInvokedAfterResetHalfTime();
    }

    private AtomicLong assertNotInvokedAfterResetHalfTime()
    {
        long fullTimeout = 100;
        Timer timer = timers.add(fullTimeout, handler, invoked);

        //  Wait half the time and check again
        long timeout = timers.timeout();
        time.set(time.get() + timeout / 2);
        int rc = timers.execute();
        assertThat(rc, is(0));

        // Reset timer and wait half of the time left
        boolean ret = timer.reset();
        assertThat(ret, is(true));

        time.set(time.get() + timeout / 2);
        rc = timers.execute();
        assertThat(rc, is(0));

        return time;
    }

    @Test
    public void testInvokedAfterReset()
    {
        assertNotInvokedAfterResetHalfTime();
        time.set(time.get() + 100 / 2);

        timers.execute();
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testReschedule()
    {
        long fullTimeout = 100;
        Timer timer = timers.add(fullTimeout, handler, invoked);

        // reschedule
        boolean ret = timer.setInterval(50);
        assertThat(ret, is(true));

        time.set(time.get() + fullTimeout / 2);
        timers.execute();
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testCancel()
    {
        long fullTimeout = 100;
        Timer timer = timers.add(fullTimeout, handler, invoked);

        // cancel timer
        long timeout = timers.timeout();
        boolean ret = timer.cancel();
        assertThat(ret, is(true));

        time.set(time.get() + timeout * 2);
        int rc = timers.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(false));
    }

    @Test
    public void testExtraLongInsertions()
    {
        int max = 100_000;
        Random random = new Random();

        List<Integer> delays = new ArrayList<>();
        List<Timer> timers = new ArrayList<>();
        for (int idx = 0; idx < max; ++idx) {
            delays.add(random.nextInt(1_000_000) + 1);
        }
        long start = System.currentTimeMillis();
        for (int idx = 0; idx < max; ++idx) {
            timers.add(this.timers.add(delays.get(idx), NOOP));
        }
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        System.out.printf("ZTimer Add: %s millisec spent on %s iterations: %s microsecs%n", elapsed, max, 1000 * elapsed / ((double) max));

        start = System.currentTimeMillis();
        long timeout = this.timers.timeout();
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTimer Timeout: %s millisec %n", elapsed);

        this.time.set(this.time.get() + timeout);
        start = System.currentTimeMillis();
        int rc = this.timers.execute();
        end = System.currentTimeMillis();
        elapsed = end - start;
        assertThat(rc > 0, is(true));
        System.out.printf("ZTimer Execute: %s millisec %n", elapsed);

        start = System.currentTimeMillis();
        for (Timer t : timers) {
            t.reset();
        }
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTimer Reset: %s millisec spent on %s iterations: %s microsecs%n", elapsed, max, 1000 * elapsed / ((double) max));

        start = System.currentTimeMillis();
        for (Timer t : timers) {
            t.cancel();
        }
        end = System.currentTimeMillis();
        elapsed = end - start;
        System.out.printf("ZTimer Cancel: %s millisec spent on %s iterations: %s microsecs%n", elapsed, max, 1000 * elapsed / ((double) max));
    }
}
