package zmq.util;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import zmq.ZMQ;
import zmq.util.function.Supplier;

/**
 * Manages set of timers.
 *
 * Timers can be added with a given interval, when the interval of time expires after addition, handler method is executed with given arguments.
 * Timer is repetitive and will be executed over time until canceled.
 *
 * This is a DRAFT class, and may change without notice.
 */
@Draft
public final class Timers
{
    public static final class Timer
    {
        private final Timers   parent;
        private long           interval;
        private boolean        alive = true;
        private final Handler  handler;
        private final Object[] args;

        private Timer(Timers parent, long interval, Handler handler, Object... args)
        {
            assert (args != null);
            this.parent = parent;
            this.interval = interval;
            this.handler = handler;
            this.args = args;
        }

        /**
         * Changes the interval of the timer.
         * This method is slow, canceling existing and adding a new timer yield better performance.
         * @param interval the new interval of the timer.
         * @return true if set, otherwise false.
         */
        public boolean setInterval(long interval)
        {
            if (alive) {
                this.interval = interval;
                return parent.insert(this);
            }
            return false;
        }

        /**
         * Reset the timer.
         * This method is slow, canceling existing and adding a new timer yield better performance.
         * @return true if reset, otherwise false.
         */
        public boolean reset()
        {
            if (alive) {
                return parent.insert(this);
            }
            return false;
        }

        /**
         * Cancels a timer.
         * @return true if cancelled, otherwise false.
         */
        public boolean cancel()
        {
            if (alive) {
                alive = false;
                return true;
            }
            return false;
        }
    }

    public interface Handler
    {
        void time(Object... args);
    }

    private final MultiMap<Long, Timer> timers = new MultiMap<>();
    private final Supplier<Long>        clock;

    public Timers()
    {
        this(() -> TimeUnit.NANOSECONDS.toMillis(Clock.nowNS()));
    }

    /**
     * Builds a new timer.
     * <p>
     * <strong>This constructor is for testing and is not intended to be used in production code.</strong>
     * @param clock the supplier of the current time in milliseconds.
     */
    public Timers(Supplier<Long> clock)
    {
        this.clock = clock;
    }

    private long now()
    {
        return clock.get();
    }

    private boolean insert(Timer timer)
    {
        return timers.insert(now() + timer.interval, timer);
    }

    /**
     * Add timer to the set, timer repeats forever, or until cancel is called.
     * @param interval the interval of repetition in milliseconds.
     * @param handler the callback called at the expiration of the timer.
     * @param args the optional arguments for the handler.
     * @return an opaque handle for further cancel.
     */
    public Timer add(long interval, Handler handler, Object... args)
    {
        if (handler == null) {
            return null;
        }
        Utils.checkArgument(interval > 0, "Delay of a timer has to be strictly greater than 0");
        final Timer timer = new Timer(this, interval, handler, args);
        final boolean rc = insert(timer);
        assert (rc);
        return timer;
    }

    /**
     * Changes the interval of the timer.
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to change the interval to.
     * @return true if set, otherwise false.
     * @deprecated use {@link Timer#setInterval(long)} instead
     */
    @Deprecated
    public boolean setInterval(Timer timer, long interval)
    {
        assert (timer.parent == this);
        return timer.setInterval(interval);
    }

    /**
     * Reset the timer.
     * This method is slow, canceling existing and adding a new timer yield better performance.
     * @param timer the timer to reset.
     * @return true if reset, otherwise false.
     * @deprecated use {@link Timer#reset()} instead
     */
    @Deprecated
    public boolean reset(Timer timer)
    {
        assert (timer.parent == this);
        return timer.reset();
    }

    /**
     * Cancel a timer.
     * @param timer the timer to cancel.
     * @return true if cancelled, otherwise false.
     * @deprecated use {@link Timer#cancel()} instead
     */
    @Deprecated
    public boolean cancel(Timer timer)
    {
        assert (timer.parent == this);
        return timer.cancel();
    }

    /**
     * Returns the time in millisecond until the next timer.
     * @return the time in millisecond until the next timer.
     */
    public long timeout()
    {
        final long now = now();
        for (Entry<Timer, Long> entry : entries()) {
            final Timer timer = entry.getKey();
            final Long expiration = entry.getValue();

            if (timer.alive) {
                //  Live timer, lets return the timeout
                if (expiration - now > 0) {
                    return expiration - now;
                }
                else {
                    return 0;
                }
            }

            //  Remove it from the list of active timers.
            timers.remove(expiration, timer);
        }
        //  Wait forever as no timers are alive
        return -1;
    }

    /**
     * Execute the timers.
     * @return the number of timers triggered.
     */
    public int execute()
    {
        int executed = 0;
        final long now = now();
        for (Entry<Timer, Long> entry : entries()) {
            final Timer timer = entry.getKey();
            final Long expiration = entry.getValue();

            //  Dead timer, lets remove it and continue
            if (!timer.alive) {
                //  Remove it from the list of active timers.
                timers.remove(expiration, timer);
                continue;
            }
            //  Map is ordered, if we have to wait for current timer we can stop.
            if (expiration - now > 0) {
                break;
            }

            insert(timer);

            timer.handler.time(timer.args);
            ++executed;
        }
        return executed;
    }

    Iterable<Entry<Timer, Long>> entries()
    {
        return timers.entries();
    }

    public int sleepAndExecute()
    {
        long timeout = timeout();
        while (timeout > 0) {
            ZMQ.msleep(timeout);
            timeout = timeout();
        }
        return execute();
    }
}
