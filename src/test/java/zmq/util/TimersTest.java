package zmq.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import zmq.ZMQ;

public class TimersTest
{
    private Timers                   timers;
    private AtomicBoolean            invoked      = new AtomicBoolean();

    private final Timers.Handler handler = new Timers.Handler()
    {
        @Override
        public void time(Object... args)
        {
            AtomicBoolean invoked = (AtomicBoolean) args[0];
            invoked.set(true);
        }
    };

    @Before
    public void setup()
    {
        timers = new Timers();
        invoked = new AtomicBoolean();

    }

    @Test
    public void testAddFaultyHandler()
    {
        Timers.Timer timer = timers.add(10, null);
        assertThat(timer, nullValue());
    }

    @Test
    public void testCancelTwice()
    {
        Timers.Timer timer = timers.add(10, handler);
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
        ZMQ.msleep(timeout / 2);
        int rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testInvoked()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);

        // Wait until the end
        timers.sleepAndExecute();
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testNotInvokedAfterHalfTimeAgain()
    {
        long fullTimeout = 100;
        timers.add(fullTimeout, handler, invoked);

        // Wait until the end
        int rc = timers.sleepAndExecute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(true));

        //  Wait half the time and check again
        long timeout = timers.timeout();
        ZMQ.msleep(timeout / 2);
        rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testNotInvokedAfterResetHalfTime()
    {
        long fullTimeout = 100;
        Timers.Timer timer = timers.add(fullTimeout, handler, invoked);

        //  Wait half the time and check again
        long timeout = timers.timeout();
        ZMQ.msleep(timeout / 2);
        int rc = timers.execute();
        assertThat(rc, is(0));

        // Reset timer and wait half of the time left
        boolean ret = timer.reset();
        assertThat(ret, is(true));

        ZMQ.msleep(timeout / 2);
        rc = timers.execute();
        assertThat(rc, is(0));
    }

    @Test
    public void testInvokedAfterReset()
    {
        testNotInvokedAfterResetHalfTime();

        // Wait until the end
        int rc = timers.sleepAndExecute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testReschedule()
    {
        long fullTimeout = 100;
        Timers.Timer timer = timers.add(fullTimeout, handler, invoked);

        // reschedule
        boolean ret = timer.setInterval(50);
        assertThat(ret, is(true));

        int rc = timers.sleepAndExecute();
        assertThat(rc, is(1));
        assertThat(invoked.get(), is(true));
    }

    @Test
    public void testCancel()
    {
        long fullTimeout = 100;
        Timers.Timer timer = timers.add(fullTimeout, handler, invoked);

        // cancel timer
        long timeout = timers.timeout();
        boolean ret = timer.cancel();
        assertThat(ret, is(true));

        ZMQ.msleep(timeout * 2);
        int rc = timers.execute();
        assertThat(rc, is(0));
        assertThat(invoked.get(), is(false));
    }
}
